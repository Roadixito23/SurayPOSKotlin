package cl.suray.posbus.domain

import cl.suray.posbus.data.prefs.AppPrefs
import cl.suray.posbus.data.prefs.LastTransaction
import cl.suray.posbus.data.prefs.Tarifa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FareModeTest {
    private val weekday = listOf(Tarifa("Público General", 3600.0))
    private val sunday = listOf(Tarifa("Público General", 4300.0))

    @Test fun unifiedWinsOverTheSwitch() {
        assertEquals(FareKind.UNIFIED, FareKind.of(unified = true, sundaySwitch = false))
        assertEquals(FareKind.UNIFIED, FareKind.of(unified = true, sundaySwitch = true))
    }

    @Test fun withoutUnifiedTheSwitchDecides() {
        assertEquals(FareKind.SUNDAY, FareKind.of(unified = false, sundaySwitch = true))
        assertEquals(FareKind.WEEKDAY, FareKind.of(unified = false, sundaySwitch = false))
    }

    @Test fun faresForUsesWeekdayPricesExceptForSunday() {
        assertEquals(weekday, faresFor(FareKind.UNIFIED, weekday, sunday))
        assertEquals(weekday, faresFor(FareKind.WEEKDAY, weekday, sunday))
        assertEquals(sunday, faresFor(FareKind.SUNDAY, weekday, sunday))
    }

    @Test fun ticketTitles() {
        assertEquals("TARIFA ÚNICA", FareKind.UNIFIED.ticketTitle)
        assertEquals("TARIFA LUNES A SÁBADO", FareKind.WEEKDAY.ticketTitle)
        assertEquals("TARIFA DOMINGO/FERIADO", FareKind.SUNDAY.ticketTitle)
    }

    @Test fun oldSaleWithoutFareKindReprintsWithItsOriginalTitle() {
        // JSON de una venta anterior al cambio (o de Flutter): solo trae switchValue.
        val sp = InMemoryPrefs()
        sp.data["lastTransaction"] = """{"nombre":"Adulto Mayor","valor":2150.0,"comprobante":"01-000009","switchValue":true}"""
        val last = AppPrefs(sp).lastTransaction!!
        assertNull(last.fareKind)
        assertEquals(FareKind.SUNDAY, last.fare)

        sp.data["lastTransaction"] = """{"nombre":"Público General","valor":3600.0,"comprobante":"01-000010","switchValue":false}"""
        assertEquals(FareKind.WEEKDAY, AppPrefs(sp).lastTransaction!!.fare)
    }

    @Test fun newSaleKeepsItsFareKind() {
        val sp = InMemoryPrefs()
        val prefs = AppPrefs(sp)
        prefs.lastTransaction = LastTransaction("Público General", 3600.0, "01-000011", switchValue = false, fareKind = FareKind.UNIFIED.name)
        val back = AppPrefs(sp).lastTransaction!!
        assertEquals("UNIFIED", back.fareKind)
        assertEquals(FareKind.UNIFIED, back.fare)
    }

    @Test fun storedFareKindBeatsSwitchValue() {
        assertEquals(FareKind.UNIFIED, FareKind.fromStored("UNIFIED", switchValue = true))
        assertEquals(FareKind.WEEKDAY, FareKind.fromStored("desconocido", switchValue = false))
    }

    @Test fun unifiedFareIsOnByDefault() {
        val sp = InMemoryPrefs()
        val prefs = AppPrefs(sp)
        assertTrue(prefs.unifiedFare)
        prefs.unifiedFare = false
        assertEquals(false, AppPrefs(sp).unifiedFare)
        assertEquals(false, sp.data["tarifa_unica"])
    }

    // --- Títulos editables -------------------------------------------------------------------

    @Test fun fareTitlesDefaultToTheCurrentTicketText() {
        val prefs = AppPrefs(InMemoryPrefs())
        assertEquals(FareTitle("TARIFA ÚNICA", "Única"), prefs.fareTitle(FareKind.UNIFIED))
        assertEquals(FareTitle("TARIFA LUNES A SÁBADO", "L-S"), prefs.fareTitle(FareKind.WEEKDAY))
        assertEquals(FareTitle("TARIFA DOMINGO/FERIADO", "D/F"), prefs.fareTitle(FareKind.SUNDAY))
    }

    @Test fun fareTitleIsSavedAsWrittenAndCanBeReset() {
        val sp = InMemoryPrefs()
        AppPrefs(sp).setFareTitle(FareKind.UNIFIED, FareTitle("Tarifa Única 2026", "Ú"))
        assertEquals(FareTitle("Tarifa Única 2026", "Ú"), AppPrefs(sp).fareTitle(FareKind.UNIFIED))
        assertEquals("Tarifa Única 2026", sp.data["fare_title_unified"])
        // Las otras tarifas no cambian.
        assertEquals(FareKind.WEEKDAY.defaultTitle, AppPrefs(sp).fareTitle(FareKind.WEEKDAY))

        AppPrefs(sp).setFareTitle(FareKind.UNIFIED, null)
        assertEquals(FareKind.UNIFIED.defaultTitle, AppPrefs(sp).fareTitle(FareKind.UNIFIED))
        assertTrue(sp.data.keys.none { it.startsWith("fare_") })
    }

    @Test fun blankStoredTitleFallsBackToDefault() {
        val sp = InMemoryPrefs()
        sp.data["fare_title_sunday"] = "  "
        assertEquals(FareKind.SUNDAY.defaultTitle, AppPrefs(sp).fareTitle(FareKind.SUNDAY))
    }

    @Test fun validateTrimsAndAcceptsUpToTheLimits() {
        val r = FareTitles.validate("  Tarifa Única  ", " L-S ")
        assertEquals(FareTitles.Result.Ok(FareTitle("Tarifa Única", "L-S")), r)
        val exact = "x".repeat(FareTitles.MAX_NAME)
        assertEquals(FareTitles.Result.Ok(FareTitle(exact, "abcd")), FareTitles.validate(exact, "abcd"))
    }

    @Test fun validateRejectsEmptyOrTooLong() {
        assertTrue(FareTitles.validate("", "L-S") is FareTitles.Result.Invalid)
        assertTrue(FareTitles.validate("   ", "L-S") is FareTitles.Result.Invalid)
        assertTrue(FareTitles.validate("x".repeat(FareTitles.MAX_NAME + 1), "L-S") is FareTitles.Result.Invalid)
        assertTrue(FareTitles.validate("Tarifa", "") is FareTitles.Result.Invalid)
        assertTrue(FareTitles.validate("Tarifa", "abcde") is FareTitles.Result.Invalid)
    }

    @Test fun lastTransactionKeepsThePrintedTitle() {
        val sp = InMemoryPrefs()
        AppPrefs(sp).lastTransaction = LastTransaction(
            "Público General", 3600.0, "01-000012", switchValue = false,
            fareKind = FareKind.UNIFIED.name, fareTitle = "Tarifa Verano",
        )
        assertEquals("Tarifa Verano", AppPrefs(sp).lastTransaction!!.fareTitle)
    }
}

/** SharedPreferences en memoria, solo para estos tests (privado para no chocar con los fakes de otras ramas). */
private class InMemoryPrefs : android.content.SharedPreferences {
    val data = LinkedHashMap<String, Any?>()
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
        override fun commit(): Boolean { ops.forEach { it() }; return true }
        override fun apply() { ops.forEach { it() } }
    }
}
