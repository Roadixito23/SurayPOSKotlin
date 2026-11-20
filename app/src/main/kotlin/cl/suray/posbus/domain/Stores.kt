package cl.suray.posbus.domain

import cl.suray.posbus.data.prefs.OpenTransaction

/** Caja abierta (`reporte_caja_transactions` / `reporte_caja_next_id`). */
interface OpenRegisterStore {
    var openTransactions: List<OpenTransaction>
    var openNextId: Int
}

/** `last_transaction_date` (yyyy-MM-dd). */
interface LastDateStore {
    var lastTransactionDate: String?
}

/** Caché anual de feriados. Sin marca guardada, el caché cuenta como fungible (igual que Flutter). */
interface HolidayStore {
    fun holidaysCache(year: Int): String?
    fun setHolidaysCache(year: Int, value: String)
    fun holidaysFungible(year: Int): Boolean
    fun setHolidaysFungible(year: Int, value: Boolean)
    fun clearHolidays(year: Int)
}

/** Agrupa escrituras de preferencias en un único commit síncrono (todo o nada). Ver `AppPrefs.batch`. */
interface PrefsBatch {
    fun <T> batch(block: () -> T): T

    /** Sin lotes: ejecuta el bloque tal cual (para tests y valores por defecto). */
    object None : PrefsBatch {
        override fun <T> batch(block: () -> T): T = block()
    }
}
