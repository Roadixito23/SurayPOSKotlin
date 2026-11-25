package cl.suray.posbus.data.prefs

import android.content.Context
import android.content.SharedPreferences
import cl.suray.posbus.data.backup.BackupPrefs
import cl.suray.posbus.data.backup.PrefEntry
import cl.suray.posbus.data.backup.PrefType
import cl.suray.posbus.domain.FareKind
import cl.suray.posbus.domain.FareTitle
import cl.suray.posbus.domain.CardPaymentStore
import cl.suray.posbus.domain.ComprobanteStore
import cl.suray.posbus.domain.HolidayStore
import cl.suray.posbus.domain.LastDateStore
import cl.suray.posbus.domain.OpenRegisterStore
import cl.suray.posbus.domain.SecurityStore
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Acceso tipado a SharedPreferences con las mismas claves y formato JSON que la app Flutter,
 * para poder importar sus preferencias tal cual.
 *
 * Las claves de seguridad se guardan en texto plano (mismo formato que Flutter); ver `domain/Security.kt`.
 *
 * Escritura síncrona: los datos de caja (transacciones abiertas, `openNextId`, `comprobanteNumber`,
 * `ticketId`, última venta y `lastTransactionDate`) se guardan con `commit()`, no con `apply()`:
 * `apply()` escribe a disco en segundo plano y un corte de energía justo después de una venta
 * podría perderla o desincronizar el correlativo. Son escrituras pequeñas y poco frecuentes, así
 * que bloquear unos milisegundos es aceptable. El resto de preferencias sigue con `apply()`.
 */
class AppPrefs(private val sp: SharedPreferences) :
    OpenRegisterStore, LastDateStore, ComprobanteStore, HolidayStore, SecurityStore, CardPaymentStore, BackupPrefs {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE),
    )

    private val json = Json { ignoreUnknownKeys = true }

    // --- Caja abierta -------------------------------------------------------------------------

    override var openTransactions: List<OpenTransaction>
        get() = decodeList(KEY_OPEN_TRANSACTIONS, OpenTransaction.serializer(), emptyList())
        set(value) = encodeList(KEY_OPEN_TRANSACTIONS, OpenTransaction.serializer(), value, durable = true)

    override var openNextId: Int
        get() = sp.getInt(KEY_OPEN_NEXT_ID, 1)
        set(value) = sp.edit().putInt(KEY_OPEN_NEXT_ID, value).save(durable = true)

    override var lastTransactionDate: String?
        get() = sp.getString(KEY_LAST_TRANSACTION_DATE, null)
        set(value) = sp.edit().putString(KEY_LAST_TRANSACTION_DATE, value).save(durable = true)

    // --- Tarifas ------------------------------------------------------------------------------

    var weekdayFares: List<Tarifa>
        get() = decodeList(KEY_PASAJES, Tarifa.serializer(), DEFAULT_WEEKDAY_FARES)
        set(value) = encodeList(KEY_PASAJES, Tarifa.serializer(), value)

    var sundayFares: List<Tarifa>
        get() = decodeList(KEY_SUNDAY_PASAJES, Tarifa.serializer(), DEFAULT_SUNDAY_FARES)
        set(value) = encodeList(KEY_SUNDAY_PASAJES, Tarifa.serializer(), value)

    /** «Tarifa única toda la semana»: se cobran los precios de lunes a sábado todos los días. Activa por defecto. */
    var unifiedFare: Boolean
        get() = sp.getBoolean(KEY_UNIFIED_FARE, true)
        set(value) = sp.edit().putBoolean(KEY_UNIFIED_FARE, value).apply()

    /** Título vigente de [kind] (`fare_title_*` / `fare_short_*`); sin clave guardada, el de fábrica. */
    fun fareTitle(kind: FareKind): FareTitle {
        val d = kind.defaultTitle
        return FareTitle(
            name = sp.getString(fareTitleKey(kind), null)?.takeIf { it.isNotBlank() } ?: d.name,
            short = sp.getString(fareShortKey(kind), null)?.takeIf { it.isNotBlank() } ?: d.short,
        )
    }

    /** Guarda el título de [kind]; null restaura el de fábrica. Validar antes con `FareTitles.validate`. */
    fun setFareTitle(kind: FareKind, title: FareTitle?) {
        sp.edit().apply {
            if (title == null) remove(fareTitleKey(kind)).remove(fareShortKey(kind))
            else putString(fareTitleKey(kind), title.name).putString(fareShortKey(kind), title.short)
        }.apply()
    }

    private fun fareTitleKey(kind: FareKind) = "$KEY_FARE_TITLE${kind.name.lowercase()}"
    private fun fareShortKey(kind: FareKind) = "$KEY_FARE_SHORT${kind.name.lowercase()}"

    // --- Rutas --------------------------------------------------------------------------------

    var rutas: List<Ruta>
        get() = decodeList(KEY_RUTAS, Ruta.serializer(), DEFAULT_RUTAS).ifEmpty { DEFAULT_RUTAS }
        set(value) = encodeList(KEY_RUTAS, Ruta.serializer(), value)

    /** Índice de la ruta activa, acotado a la lista actual. */
    var rutaActiva: Int
        get() = sp.getInt(KEY_RUTA_ACTIVA, 0).coerceIn(0, rutas.size - 1)
        set(value) = sp.edit().putInt(KEY_RUTA_ACTIVA, value).apply()

    /** `true` = sentido de vuelta (destino → origen) de la ruta activa. */
    var rutaInvertida: Boolean
        get() = sp.getBoolean(KEY_RUTA_INVERTIDA, false)
        set(value) = sp.edit().putBoolean(KEY_RUTA_INVERTIDA, value).apply()

    // --- Comprobante --------------------------------------------------------------------------

    override var comprobanteNumber: Int
        get() = sp.getInt(KEY_COMPROBANTE_NUMBER, 0)
        set(value) = sp.edit().putInt(KEY_COMPROBANTE_NUMBER, value).save(durable = true)

    override var ticketId: Int
        get() = sp.getInt(KEY_TICKET_ID, 1)
        set(value) = sp.edit().putInt(KEY_TICKET_ID, value).save(durable = true)

    // --- Feriados -----------------------------------------------------------------------------

    override fun holidaysCache(year: Int): String? = sp.getString("$KEY_HOLIDAYS_CACHE$year", null)
    override fun setHolidaysCache(year: Int, value: String) =
        sp.edit().putString("$KEY_HOLIDAYS_CACHE$year", value).apply()

    override fun holidaysFungible(year: Int): Boolean = sp.getBoolean("$KEY_HOLIDAYS_FUNGIBLE$year", true)
    override fun setHolidaysFungible(year: Int, value: Boolean) =
        sp.edit().putBoolean("$KEY_HOLIDAYS_FUNGIBLE$year", value).apply()

    override fun clearHolidays(year: Int) {
        sp.edit().remove("$KEY_HOLIDAYS_CACHE$year").remove("$KEY_HOLIDAYS_FUNGIBLE$year").apply()
    }

    // --- Pagos con tarjeta (por día, yyyy-MM-dd) ----------------------------------------------

    override fun cardPayments(date: String): String? = sp.getString("$KEY_CARD_PAYMENTS$date", null)
    override fun setCardPayments(date: String, value: String) =
        sp.edit().putString("$KEY_CARD_PAYMENTS$date", value).apply()

    /** Borra los pagos de días anteriores a [today] (yyyy-MM-dd; el orden lexicográfico sirve). */
    fun clearCardPaymentsBefore(today: String) {
        val stale = sp.all.keys.filter {
            it.startsWith(KEY_CARD_PAYMENTS) && it.removePrefix(KEY_CARD_PAYMENTS) < today
        }
        if (stale.isNotEmpty()) sp.edit().apply { stale.forEach(::remove) }.apply()
    }

    // --- NFC / SumUp --------------------------------------------------------------------------

    var linkedNfcCards: List<String>
        get() = decodeList(KEY_LINKED_NFC_CARDS, String.serializer(), emptyList())
        set(value) = encodeList(KEY_LINKED_NFC_CARDS, String.serializer(), value)

    var sumUpEnabled: Boolean
        get() = sp.getBoolean(KEY_SUMUP_ENABLED, true)
        set(value) = sp.edit().putBoolean(KEY_SUMUP_ENABLED, value).apply()

    /** Sin valor por defecto: la clave de afiliado solo se escribe en Ajustes, nunca va en el código ni en el APK. */
    var sumUpAffiliateKey: String?
        get() = sp.getString(KEY_SUMUP_AFFILIATE_KEY, null)
        set(value) = sp.edit().putString(KEY_SUMUP_AFFILIATE_KEY, value).apply()

    // --- Última venta (para anular / reimprimir) ------------------------------------------------

    var lastTransaction: LastTransaction?
        get() = sp.getString(KEY_LAST_TRANSACTION, null)?.let {
            runCatching { json.decodeFromString(LastTransaction.serializer(), it) }.getOrNull()
        }
        set(value) = putOrRemove(
            KEY_LAST_TRANSACTION,
            value?.let { json.encodeToString(LastTransaction.serializer(), it) },
            durable = true,
        )

    // --- Colores de botones (`button_colors_{weekday|sunday|otros}_N`) -------------------------

    fun buttonColorsJson(key: String): String? = sp.getString("button_colors_$key", null)

    /** `json == null` borra la personalización y el botón vuelve a su color de fábrica. */
    fun setButtonColorsJson(key: String, json: String?) = putOrRemove("button_colors_$key", json)

    // --- Apariencia / PDF ---------------------------------------------------------------------

    var showIcons: Boolean
        get() = sp.getBoolean(KEY_SHOW_ICONS, true)
        set(value) = sp.edit().putBoolean(KEY_SHOW_ICONS, value).apply()

    var buttonOpacity: Float
        get() = sp.getFloat(KEY_BUTTON_OPACITY, 1f)
        set(value) = sp.edit().putFloat(KEY_BUTTON_OPACITY, value).apply()

    var iconSpacing: Float
        get() = sp.getFloat(KEY_ICON_SPACING, 1f)
        set(value) = sp.edit().putFloat(KEY_ICON_SPACING, value).apply()

    var textSizeMultiplier: Float
        get() = sp.getFloat(KEY_TEXT_SIZE_MULTIPLIER, 0.8f)
        set(value) = sp.edit().putFloat(KEY_TEXT_SIZE_MULTIPLIER, value).apply()

    var pdfEndMargin: Float
        get() = sp.getFloat(KEY_PDF_END_MARGIN, DEFAULT_PDF_END_MARGIN)
        set(value) = sp.edit().putFloat(KEY_PDF_END_MARGIN, value).apply()

    /** Abreviatura de tarifa para el PDF; la clave de preferencia es el nombre de la tarifa (como en Flutter). */
    fun abbreviation(name: String): String = sp.getString(name, null) ?: DEFAULT_ABBREVIATIONS[name] ?: name

    fun setAbbreviation(name: String, value: String) = sp.edit().putString(name, value).apply()

    // --- Seguridad ----------------------------------------------------------------------------

    override var password: String?
        get() = sp.getString(KEY_PASSWORD, null)
        set(value) = putOrRemove(KEY_PASSWORD, value)

    override var settingsPassword: String?
        get() = sp.getString(KEY_SETTINGS_PASSWORD, null)
        set(value) = putOrRemove(KEY_SETTINGS_PASSWORD, value)

    override var comprobanteSettingsPassword: String?
        get() = sp.getString(KEY_COMPROBANTE_SETTINGS_PASSWORD, null)
        set(value) = putOrRemove(KEY_COMPROBANTE_SETTINGS_PASSWORD, value)

    override var failedAttempts: Int
        get() = sp.getInt(KEY_FAILED_ATTEMPTS, 0)
        set(value) = sp.edit().putInt(KEY_FAILED_ATTEMPTS, value).apply()

    override var lastLockTime: Long
        get() = sp.getLong(KEY_LAST_LOCK_TIME, 0L)
        set(value) = sp.edit().apply { if (value > 0L) putLong(KEY_LAST_LOCK_TIME, value) else remove(KEY_LAST_LOCK_TIME) }.apply()

    // --- Respaldo -----------------------------------------------------------------------------

    override fun snapshot(): Map<String, Any?> = sp.all

    /** Escribe cada valor con el tipo que esta clase lee (`getInt`, `getLong`, `getFloat`…); un tipo equivocado lanzaría al leerlo. */
    override fun apply(entries: List<PrefEntry>) {
        val e = sp.edit()
        for ((key, type, value) in entries) {
            when (type) {
                PrefType.String -> e.putString(key, value as String)
                PrefType.Bool -> e.putBoolean(key, value as Boolean)
                PrefType.Int -> (value as Long).let { n ->
                    if (key in LONG_KEYS || n !in Int.MIN_VALUE..Int.MAX_VALUE) e.putLong(key, n) else e.putInt(key, n.toInt())
                }
                PrefType.Double -> e.putFloat(key, (value as Double).toFloat())
                @Suppress("UNCHECKED_CAST")
                PrefType.StringList -> e.putStringSet(key, (value as List<String>).toSet())
            }
        }
        e.apply()
    }

    // --- Utilidades ---------------------------------------------------------------------------

    /** [durable] = true escribe con `commit()` (ver "Escritura síncrona" en el KDoc de la clase). */
    private fun putOrRemove(key: String, value: String?, durable: Boolean = false) {
        sp.edit().apply { if (value == null) remove(key) else putString(key, value) }.save(durable)
    }

    private fun SharedPreferences.Editor.save(durable: Boolean) {
        if (durable) commit() else apply()
    }

    private fun <T> decodeList(
        key: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        default: List<T>,
    ): List<T> {
        val raw = sp.getString(key, null) ?: return default
        return runCatching { json.decodeFromString(ListSerializer(serializer), raw) }.getOrDefault(default)
    }

    private fun <T> encodeList(
        key: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        value: List<T>,
        durable: Boolean = false,
    ) {
        sp.edit().putString(key, json.encodeToString(ListSerializer(serializer), value)).save(durable)
    }

    companion object {
        const val FILE = "suraypos_prefs"

        const val KEY_OPEN_TRANSACTIONS = "reporte_caja_transactions"
        const val KEY_OPEN_NEXT_ID = "reporte_caja_next_id"
        const val KEY_LAST_TRANSACTION_DATE = "last_transaction_date"
        const val KEY_LAST_TRANSACTION = "lastTransaction"
        const val KEY_PASAJES = "pasajes"
        const val KEY_SUNDAY_PASAJES = "sunday_pasajes"
        const val KEY_UNIFIED_FARE = "tarifa_unica"
        const val KEY_FARE_TITLE = "fare_title_"
        const val KEY_FARE_SHORT = "fare_short_"
        const val KEY_RUTAS = "rutas"
        const val KEY_RUTA_ACTIVA = "ruta_activa"
        const val KEY_RUTA_INVERTIDA = "ruta_invertida"
        const val KEY_COMPROBANTE_NUMBER = "comprobanteNumber"
        const val KEY_TICKET_ID = "ticketId"
        const val KEY_HOLIDAYS_CACHE = "holidays_cache_"
        const val KEY_HOLIDAYS_FUNGIBLE = "holidays_fungible_"
        const val KEY_CARD_PAYMENTS = "pagos_tarjeta_"
        const val KEY_LINKED_NFC_CARDS = "linked_nfc_cards"
        const val KEY_SUMUP_ENABLED = "sumUpEnabled"
        const val KEY_SUMUP_AFFILIATE_KEY = "sumup_affiliate_key"
        const val KEY_SHOW_ICONS = "showIcons"
        const val KEY_BUTTON_OPACITY = "buttonOpacity"
        const val KEY_ICON_SPACING = "iconSpacing"
        const val KEY_TEXT_SIZE_MULTIPLIER = "textSizeMultiplier"
        const val KEY_PDF_END_MARGIN = "pdfEndMargin"

        // Seguridad
        const val KEY_PASSWORD = "password"
        const val KEY_SETTINGS_PASSWORD = "settings_password"
        const val KEY_COMPROBANTE_SETTINGS_PASSWORD = "comprobanteSettingsPassword"
        const val KEY_FAILED_ATTEMPTS = "failedAttempts"
        const val KEY_LAST_LOCK_TIME = "lastLockTime"

        const val DEFAULT_PDF_END_MARGIN = 69f

        /** Claves guardadas como `Long` (el resto de enteros son `Int`). */
        private val LONG_KEYS = setOf(KEY_LAST_LOCK_TIME)

        val DEFAULT_ABBREVIATIONS = linkedMapOf(
            "Público General" to "PG",
            "Escolar General" to "Esc.",
            "Adulto Mayor" to "AM",
            "Escolar Intermedio" to "Int.E",
            "Intermedio hasta 15 Km" to "Int.15",
            "Intermedio hasta 50 Km" to "Int.50",
            "Oferta Ruta" to "OR",
            "Cargo" to "Cargo",
        )

        val DEFAULT_RUTAS = listOf(Ruta("Aysén", "Coyhaique"))

        val DEFAULT_WEEKDAY_FARES = listOf(
            Tarifa("Público General", 3600.0),
            Tarifa("Escolar General", 2500.0),
            Tarifa("Adulto Mayor", 1800.0),
            Tarifa("Int. hasta 15 Km", 1800.0),
            Tarifa("Int. hasta 50 Km", 2500.0),
            Tarifa("Escolar Intermedio", 1000.0),
        )

        val DEFAULT_SUNDAY_FARES = listOf(
            Tarifa("Público General", 4300.0),
            Tarifa("Escolar General", 3000.0),
            Tarifa("Adulto Mayor", 2150.0),
            Tarifa("Int. hasta 15 Km", 3000.0),
            Tarifa("Int. hasta 50 Km", 3000.0),
            Tarifa("Escolar Intermedio", 1300.0),
        )
    }
}
