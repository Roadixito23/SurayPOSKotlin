package cl.suray.posbus.domain

import cl.suray.posbus.data.prefs.OpenTransaction
import java.util.TimeZone

val UTC: TimeZone = TimeZone.getTimeZone("UTC")

class FakeClock(var millis: Long) : Clock {
    override fun nowMillis() = millis

    /** Mediodía UTC del día indicado. */
    fun setDay(y: Int, m: Int, d: Int) { millis = (LocalDay(y, m, d).toEpochDay() * 86_400_000L) + 12 * 3_600_000L }

    companion object {
        fun at(y: Int, m: Int, d: Int) = FakeClock(0).also { it.setDay(y, m, d) }
    }
}

class FakeRegisterStore : OpenRegisterStore {
    override var openTransactions: List<OpenTransaction> = emptyList()
    override var openNextId: Int = 1
}

class FakeComprobanteStore(override var comprobanteNumber: Int = 0, override var ticketId: Int = 1) : ComprobanteStore

class FakeHolidayStore : HolidayStore {
    val cache = HashMap<Int, String>()
    val fungible = HashMap<Int, Boolean>()
    override fun holidaysCache(year: Int) = cache[year]
    override fun setHolidaysCache(year: Int, value: String) { cache[year] = value }
    override fun holidaysFungible(year: Int) = fungible[year] ?: true
    override fun setHolidaysFungible(year: Int, value: Boolean) { fungible[year] = value }
    override fun clearHolidays(year: Int) { cache.remove(year); fungible.remove(year) }
}

fun tx(
    id: Int, nombre: String, valor: Double, dia: String = "21", mes: String = "10", ano: String? = "2026",
    comprobante: String = "01-%06d".format(id), hora: String = "10:00", timestamp: Long = id.toLong(),
) = OpenTransaction(id, nombre, valor, comprobante, dia, mes, ano, hora, timestamp)
