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

/** SharedPreferences en memoria que cuenta cuántas veces se confirma con `commit()` y con `apply()`. */
class FakeSharedPreferences : android.content.SharedPreferences {
    val data = LinkedHashMap<String, Any?>()
    var commits = 0
    var applies = 0
    /** Si es false, `commit()` no escribe y devuelve false (disco lleno / error de E/S). */
    var commitSucceeds = true

    override fun getAll(): MutableMap<String, *> = HashMap(data)
    override fun getString(key: String, defValue: String?) = data[key] as String? ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
        @Suppress("UNCHECKED_CAST") return data[key] as MutableSet<String>? ?: defValues
    }
    override fun getInt(key: String, defValue: Int) = data[key] as Int? ?: defValue
    override fun getLong(key: String, defValue: Long) = data[key] as Long? ?: defValue
    override fun getFloat(key: String, defValue: Float) = data[key] as Float? ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = data[key] as Boolean? ?: defValue
    override fun contains(key: String) = data.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun edit(): android.content.SharedPreferences.Editor = object : android.content.SharedPreferences.Editor {
        private val ops = ArrayList<() -> Unit>()
        private fun put(key: String, value: Any?) = apply { ops += { data[key] = value } }
        override fun putString(key: String, value: String?) = put(key, value)
        override fun putStringSet(key: String, values: MutableSet<String>?) = put(key, values)
        override fun putInt(key: String, value: Int) = put(key, value)
        override fun putLong(key: String, value: Long) = put(key, value)
        override fun putFloat(key: String, value: Float) = put(key, value)
        override fun putBoolean(key: String, value: Boolean) = put(key, value)
        override fun remove(key: String) = apply { ops += { data.remove(key) } }
        override fun clear() = apply { ops += { data.clear() } }
        override fun commit(): Boolean {
            commits++
            if (!commitSucceeds) return false
            ops.forEach { it() }
            return true
        }
        override fun apply() {
            applies++
            ops.forEach { it() }
        }
    }
}
