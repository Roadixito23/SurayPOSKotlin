package cl.suray.posbus.domain

import cl.suray.posbus.data.prefs.Tarifa

/**
 * Nombre editable de una tarifa: [name] se imprime en el ticket tal cual lo escribe el usuario y titula la
 * tarifa en Ajustes; [short] es la abreviatura del interruptor de Inicio (L-S / D/F).
 */
data class FareTitle(val name: String, val short: String)

/** Qué tarifa se cobró: la única de toda la semana, o la de lunes a sábado / domingo y feriados (modo diferenciado). */
enum class FareKind(val defaultTitle: FareTitle) {
    UNIFIED(FareTitle("TARIFA ÚNICA", "Única")),
    WEEKDAY(FareTitle("TARIFA LUNES A SÁBADO", "L-S")),
    SUNDAY(FareTitle("TARIFA DOMINGO/FERIADO", "D/F")),
    ;

    /** Título de fábrica del ticket (el vigente se lee con `AppPrefs.fareTitle`). */
    val ticketTitle: String get() = defaultTitle.name

    companion object {
        /** Con tarifa única manda esta; si no, el interruptor L-S / D/F. */
        fun of(unified: Boolean, sundaySwitch: Boolean): FareKind =
            if (unified) UNIFIED else if (sundaySwitch) SUNDAY else WEEKDAY

        /**
         * Lo guardado en la última venta: [stored] (`fareKind`) si existe y es válido; en ventas antiguas o de Flutter
         * solo hay `switchValue`, que decide entre domingo/feriado y lunes a sábado.
         */
        fun fromStored(stored: String?, switchValue: Boolean): FareKind =
            entries.firstOrNull { it.name == stored } ?: of(unified = false, sundaySwitch = switchValue)
    }
}

/** Lista de precios de [kind]: la de lunes a sábado salvo en domingo/feriado diferenciado. */
fun faresFor(kind: FareKind, weekday: List<Tarifa>, sunday: List<Tarifa>): List<Tarifa> =
    if (kind == FareKind.SUNDAY) sunday else weekday

/** Reglas de los títulos editables: sin espacios sobrantes, no vacíos, nombre ≤ 24 (58 mm) y abreviatura ≤ 4. */
object FareTitles {
    const val MAX_NAME = 24
    const val MAX_SHORT = 4

    sealed interface Result {
        data class Ok(val title: FareTitle) : Result
        data class Invalid(val reason: String) : Result
    }

    fun validate(name: String, short: String): Result {
        val n = name.trim()
        val s = short.trim()
        return when {
            n.isEmpty() -> Result.Invalid("El nombre no puede estar vacío")
            n.length > MAX_NAME -> Result.Invalid("El nombre admite hasta $MAX_NAME caracteres")
            s.isEmpty() -> Result.Invalid("La abreviatura no puede estar vacía")
            s.length > MAX_SHORT -> Result.Invalid("La abreviatura admite hasta $MAX_SHORT caracteres")
            else -> Result.Ok(FareTitle(n, s))
        }
    }
}
