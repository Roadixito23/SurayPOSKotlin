package cl.suray.posbus.data.prefs

import android.content.Context
import android.content.SharedPreferences
import cl.suray.posbus.data.backup.BackupPrefs
import cl.suray.posbus.data.backup.PrefEntry
import cl.suray.posbus.data.backup.PrefType
import cl.suray.posbus.domain.CardPaymentStore
import cl.suray.posbus.domain.ComprobanteStore
import cl.suray.posbus.domain.HolidayStore
import cl.suray.posbus.domain.LastDateStore
import cl.suray.posbus.domain.OpenRegisterStore
import cl.suray.posbus.domain.PrefsBatch
import cl.suray.posbus.domain.SecurityStore
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.io.IOException
import kotlinx.serialization.json.Json

/**
 * Acceso tipado a SharedPreferences con las mismas claves y formato JSON que la app Flutter,
 * para poder importar sus preferencias tal cual.
 *
 * Las claves de seguridad se guardan en texto plano (mismo formato que Flutter); ver `domain/Security.kt`.
 */
class AppPrefs(private val sp: SharedPreferences) :
    OpenRegisterStore, LastDateStore, ComprobanteStore, HolidayStore, SecurityStore, CardPaymentStore, BackupPrefs, PrefsBatch {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE),
    )

    private val json = Json { ignoreUnknownKeys = true }

    // --- Lotes: varias escrituras confirmadas juntas con un solo commit() -----------------------

    private val batchLock = Any()
    @Volatile private var batchEditor: TrackingEditor? = null
    @Volatile private var batchOwner: Thread? = null

    /** Lo escrito en el lote en curso (null = borrado); así las lecturas del mismo hilo ven sus propias escrituras. */
    private val pending = HashMap<String, Any?>()

    /** Editor que recuerda en [pending] lo escrito, para poder leerlo antes del commit. */
    private inner class TrackingEditor(private val d: SharedPreferences.Editor) : SharedPreferences.Editor by d {
        override fun putString(key: String, value: String?) = apply { pending[key] = value; d.putString(key, value) }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value; d.putInt(key, value) }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value; d.putLong(key, value) }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value; d.putFloat(key, value) }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value; d.putBoolean(key, value) }
        override fun remove(key: String) = apply { pending[key] = null; d.remove(key) }
    }

    /**
     * Agrupa todas las escrituras de [block] en un único `commit()` síncrono: o se guarda todo o nada.
     * Si [block] lanza una excepción no se escribe nada; si `commit()` falla se lanza [IOException].
     * Es reentrante: un lote anidado se une al exterior, que es el único que confirma.
     * Solo el hilo que abrió el lote escribe en él; los demás hilos siguen usando `apply()`.
     */
    override fun <T> batch(block: () -> T): T = synchronized(batchLock) {
        if (batchEditor != null) return@synchronized block()
        val editor = TrackingEditor(sp.edit())
        batchEditor = editor
        batchOwner = Thread.currentThread()
        try {
            val result = block()
            if (!editor.commit()) throw IOException("No se pudo guardar en el almacenamiento")
            result
        } finally {
            batchEditor = null
            batchOwner = null
            pending.clear()
        }
    }

    private fun inBatch() = batchEditor != null && batchOwner === Thread.currentThread()

    /** Escribe en el lote en curso o, fuera de uno, con `apply()` inmediato. */
    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        if (inBatch()) batchEditor!!.block() else sp.edit().apply(block).apply()
    }

    private fun <T> peek(key: String, committed: () -> T, whenRemoved: T): T {
        if (!inBatch() || !pending.containsKey(key)) return committed()
        @Suppress("UNCHECKED_CAST")
        return (pending[key] as T?) ?: whenRemoved
    }

    private fun getStr(key: String, def: String?): String? = peek(key, { sp.getString(key, def) }, def)
    private fun getIntV(key: String, def: Int): Int = peek(key, { sp.getInt(key, def) }, def)
    private fun getLongV(key: String, def: Long): Long = peek(key, { sp.getLong(key, def) }, def)
    private fun getFloatV(key: String, def: Float): Float = peek(key, { sp.getFloat(key, def) }, def)
    private fun getBool(key: String, def: Boolean): Boolean = peek(key, { sp.getBoolean(key, def) }, def)

    // --- Caja abierta -------------------------------------------------------------------------

    override var openTransactions: List<OpenTransaction>
        get() = decodeList(KEY_OPEN_TRANSACTIONS, OpenTransaction.serializer(), emptyList())
        set(value) = encodeList(KEY_OPEN_TRANSACTIONS, OpenTransaction.serializer(), value)

    override var openNextId: Int
        get() = getIntV(KEY_OPEN_NEXT_ID, 1)
        set(value) = edit { putInt(KEY_OPEN_NEXT_ID, value) }

    override var lastTransactionDate: String?
        get() = getStr(KEY_LAST_TRANSACTION_DATE, null)
        set(value) = edit { putString(KEY_LAST_TRANSACTION_DATE, value) }

    // --- Tarifas ------------------------------------------------------------------------------

    var weekdayFares: List<Tarifa>
        get() = decodeList(KEY_PASAJES, Tarifa.serializer(), DEFAULT_WEEKDAY_FARES)
        set(value) = encodeList(KEY_PASAJES, Tarifa.serializer(), value)

    var sundayFares: List<Tarifa>
        get() = decodeList(KEY_SUNDAY_PASAJES, Tarifa.serializer(), DEFAULT_SUNDAY_FARES)
        set(value) = encodeList(KEY_SUNDAY_PASAJES, Tarifa.serializer(), value)

    // --- Comprobante --------------------------------------------------------------------------

    override var comprobanteNumber: Int
        get() = getIntV(KEY_COMPROBANTE_NUMBER, 0)
        set(value) = edit { putInt(KEY_COMPROBANTE_NUMBER, value) }

    override var ticketId: Int
        get() = getIntV(KEY_TICKET_ID, 1)
        set(value) = edit { putInt(KEY_TICKET_ID, value) }

    // --- Feriados -----------------------------------------------------------------------------

    override fun holidaysCache(year: Int): String? = getStr("$KEY_HOLIDAYS_CACHE$year", null)
    override fun setHolidaysCache(year: Int, value: String) =
        edit { putString("$KEY_HOLIDAYS_CACHE$year", value) }

    override fun holidaysFungible(year: Int): Boolean = getBool("$KEY_HOLIDAYS_FUNGIBLE$year", true)
    override fun setHolidaysFungible(year: Int, value: Boolean) =
        edit { putBoolean("$KEY_HOLIDAYS_FUNGIBLE$year", value) }

    override fun clearHolidays(year: Int) {
        edit { remove("$KEY_HOLIDAYS_CACHE$year").remove("$KEY_HOLIDAYS_FUNGIBLE$year") }
    }

    // --- Pagos con tarjeta (por día, yyyy-MM-dd) ----------------------------------------------

    override fun cardPayments(date: String): String? = getStr("$KEY_CARD_PAYMENTS$date", null)
    override fun setCardPayments(date: String, value: String) =
        edit { putString("$KEY_CARD_PAYMENTS$date", value) }

    /** Borra los pagos de días anteriores a [today] (yyyy-MM-dd; el orden lexicográfico sirve). */
    fun clearCardPaymentsBefore(today: String) {
        val stale = sp.all.keys.filter {
            it.startsWith(KEY_CARD_PAYMENTS) && it.removePrefix(KEY_CARD_PAYMENTS) < today
        }
        if (stale.isNotEmpty()) edit { stale.forEach { remove(it) } }
    }

    // --- NFC / SumUp --------------------------------------------------------------------------

    var linkedNfcCards: List<String>
        get() = decodeList(KEY_LINKED_NFC_CARDS, String.serializer(), emptyList())
        set(value) = encodeList(KEY_LINKED_NFC_CARDS, String.serializer(), value)

    var sumUpEnabled: Boolean
        get() = getBool(KEY_SUMUP_ENABLED, true)
        set(value) = edit { putBoolean(KEY_SUMUP_ENABLED, value) }

    /** Sin valor por defecto: la clave de afiliado solo se escribe en Ajustes, nunca va en el código ni en el APK. */
    var sumUpAffiliateKey: String?
        get() = getStr(KEY_SUMUP_AFFILIATE_KEY, null)
        set(value) = edit { putString(KEY_SUMUP_AFFILIATE_KEY, value) }

    // --- Última venta (para anular / reimprimir) ------------------------------------------------

    var lastTransaction: LastTransaction?
        get() = getStr(KEY_LAST_TRANSACTION, null)?.let {
            runCatching { json.decodeFromString(LastTransaction.serializer(), it) }.getOrNull()
        }
        set(value) = putOrRemove(
            KEY_LAST_TRANSACTION,
            value?.let { json.encodeToString(LastTransaction.serializer(), it) },
        )

    // --- Colores de botones (`button_colors_{weekday|sunday|otros}_N`) -------------------------

    fun buttonColorsJson(key: String): String? = getStr("button_colors_$key", null)

    /** `json == null` borra la personalización y el botón vuelve a su color de fábrica. */
    fun setButtonColorsJson(key: String, json: String?) = putOrRemove("button_colors_$key", json)

    // --- Apariencia / PDF ---------------------------------------------------------------------

    var showIcons: Boolean
        get() = getBool(KEY_SHOW_ICONS, true)
        set(value) = edit { putBoolean(KEY_SHOW_ICONS, value) }

    var buttonOpacity: Float
        get() = getFloatV(KEY_BUTTON_OPACITY, 1f)
        set(value) = edit { putFloat(KEY_BUTTON_OPACITY, value) }

    var iconSpacing: Float
        get() = getFloatV(KEY_ICON_SPACING, 1f)
        set(value) = edit { putFloat(KEY_ICON_SPACING, value) }

    var textSizeMultiplier: Float
        get() = getFloatV(KEY_TEXT_SIZE_MULTIPLIER, 0.8f)
        set(value) = edit { putFloat(KEY_TEXT_SIZE_MULTIPLIER, value) }

    var pdfEndMargin: Float
        get() = getFloatV(KEY_PDF_END_MARGIN, DEFAULT_PDF_END_MARGIN)
        set(value) = edit { putFloat(KEY_PDF_END_MARGIN, value) }

    /** Abreviatura de tarifa para el PDF; la clave de preferencia es el nombre de la tarifa (como en Flutter). */
    fun abbreviation(name: String): String = getStr(name, null) ?: DEFAULT_ABBREVIATIONS[name] ?: name

    fun setAbbreviation(name: String, value: String) = edit { putString(name, value) }

    // --- Seguridad ----------------------------------------------------------------------------

    override var password: String?
        get() = getStr(KEY_PASSWORD, null)
        set(value) = putOrRemove(KEY_PASSWORD, value)

    override var settingsPassword: String?
        get() = getStr(KEY_SETTINGS_PASSWORD, null)
        set(value) = putOrRemove(KEY_SETTINGS_PASSWORD, value)

    override var comprobanteSettingsPassword: String?
        get() = getStr(KEY_COMPROBANTE_SETTINGS_PASSWORD, null)
        set(value) = putOrRemove(KEY_COMPROBANTE_SETTINGS_PASSWORD, value)

    override var failedAttempts: Int
        get() = getIntV(KEY_FAILED_ATTEMPTS, 0)
        set(value) = edit { putInt(KEY_FAILED_ATTEMPTS, value) }

    override var lastLockTime: Long
        get() = getLongV(KEY_LAST_LOCK_TIME, 0L)
        set(value) = edit { if (value > 0L) putLong(KEY_LAST_LOCK_TIME, value) else remove(KEY_LAST_LOCK_TIME) }

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

    private fun putOrRemove(key: String, value: String?) {
        edit { if (value == null) remove(key) else putString(key, value) }
    }

    private fun <T> decodeList(
        key: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        default: List<T>,
    ): List<T> {
        val raw = getStr(key, null) ?: return default
        return runCatching { json.decodeFromString(ListSerializer(serializer), raw) }.getOrDefault(default)
    }

    private fun <T> encodeList(key: String, serializer: kotlinx.serialization.KSerializer<T>, value: List<T>) {
        edit { putString(key, json.encodeToString(ListSerializer(serializer), value)) }
    }

    companion object {
        const val FILE = "suraypos_prefs"

        const val KEY_OPEN_TRANSACTIONS = "reporte_caja_transactions"
        const val KEY_OPEN_NEXT_ID = "reporte_caja_next_id"
        const val KEY_LAST_TRANSACTION_DATE = "last_transaction_date"
        const val KEY_LAST_TRANSACTION = "lastTransaction"
        const val KEY_PASAJES = "pasajes"
        const val KEY_SUNDAY_PASAJES = "sunday_pasajes"
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
