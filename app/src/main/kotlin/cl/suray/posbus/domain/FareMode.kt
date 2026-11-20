package cl.suray.posbus.domain

import cl.suray.posbus.data.prefs.Tarifa

/** Qué tarifa se cobró: la única de toda la semana, o la de lunes a sábado / domingo y feriados (modo diferenciado). */
enum class FareKind(val ticketTitle: String) {
    UNIFIED("TARIFA ÚNICA"),
    WEEKDAY("TARIFA LUNES A SÁBADO"),
    SUNDAY("TARIFA DOMINGO/FERIADO"),
    ;

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
