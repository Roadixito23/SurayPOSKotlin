package cl.suray.posbus.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val HOLIDAYS_2026 = """[{"nombre":"Año Nuevo","fecha":"2026-01-01","tipo":"Civil"},{"nombre":"Viernes Santo","fecha":"2026-04-03"}]"""

class HolidayServiceTest {
    private class CountingFetcher(var body: String?) : HolidayFetcher {
        var calls = 0
        override fun fetch(year: Int): String? { calls++; return body }
    }

    private fun service(store: FakeHolidayStore, fetcher: HolidayFetcher, clock: FakeClock) =
        HolidayService(store, fetcher, clock, UTC)

    @Test fun sundayNeedsNoApi() {
        val f = CountingFetcher(null)
        val r = service(FakeHolidayStore(), f, FakeClock.at(2026, 10, 25)).checkToday()
        assertEquals(HolidayCheckResult(true, "Domingo"), r)
        assertEquals(0, f.calls)
    }

    @Test fun holidayAndOrdinaryDay() {
        val f = CountingFetcher(HOLIDAYS_2026)
        val s = FakeHolidayStore()
        assertEquals(HolidayCheckResult(true, "Viernes Santo"), service(s, f, FakeClock.at(2026, 4, 3)).checkToday())
        assertEquals(HolidayCheckResult(false, null), service(s, f, FakeClock.at(2026, 4, 6)).checkToday())
        assertEquals(1, f.calls) // el segundo usa el caché definitivo
    }

    @Test fun cacheFetchedBeforeJan5IsFungibleAndRenewedOnJan5() {
        val f = CountingFetcher(HOLIDAYS_2026)
        val s = FakeHolidayStore()
        service(s, f, FakeClock.at(2026, 1, 2)).checkToday()          // lunes, antes del 5
        assertTrue(s.fungible[2026]!!)
        service(s, f, FakeClock.at(2026, 1, 3)).checkToday()          // sábado: sigue vigente
        assertEquals(1, f.calls)
        service(s, f, FakeClock.at(2026, 1, 5)).checkToday()          // 5 de enero: renueva
        assertEquals(2, f.calls)
        assertFalse(s.fungible[2026]!!)
        service(s, f, FakeClock.at(2026, 6, 10)).checkToday()
        assertEquals(2, f.calls)
    }

    @Test fun apiFailureFallsBackToStaleCacheOrEmpty() {
        val s = FakeHolidayStore()
        val f = CountingFetcher(null)
        assertEquals(HolidayCheckResult(false, null), service(s, f, FakeClock.at(2026, 4, 3)).checkToday())

        s.cache[2026] = HOLIDAYS_2026; s.fungible[2026] = true       // caché fungible vencido
        assertEquals(HolidayCheckResult(true, "Viernes Santo"), service(s, f, FakeClock.at(2026, 4, 3)).checkToday())
    }

    @Test fun corruptBodyIsTreatedAsEmpty() {
        val r = service(FakeHolidayStore(), CountingFetcher("<html>error"), FakeClock.at(2026, 4, 3)).checkToday()
        assertFalse(r.isSpecial)
    }

    @Test fun refreshDiscardsCache() {
        val f = CountingFetcher(HOLIDAYS_2026)
        val s = FakeHolidayStore()
        val svc = service(s, f, FakeClock.at(2026, 4, 3))
        svc.checkToday(); svc.refresh()
        assertEquals(2, f.calls)
    }
}

class StatisticsTest {
    private val today = LocalDay(2026, 10, 21) // miércoles

    @Test fun emptyInputGivesEmptyStats() {
        assertEquals(SalesStats(), Statistics.sales(emptyList(), today))
    }

    @Test fun totalsAveragesAndBest() {
        val data = listOf(
            tx(1, "Público General", 3600.0, dia = "19"),            // lunes
            tx(2, "Público General", 3600.0, dia = "19"),
            tx(3, "Adulto Mayor", 1800.0, dia = "20"),               // martes
            tx(4, "Cargo: Caja", 2500.0, dia = "20"),
            tx(5, "Anulación: Adulto Mayor", -1800.0, dia = "20"),
            tx(6, "Público General", 4300.0, dia = "18"),            // domingo
        )
        val s = Statistics.sales(data, today)
        assertEquals(3600.0 * 2 + 1800 + 2500 + 4300, s.totalVentas, 0.0)
        assertEquals(2500.0, s.totalCorrespondencias, 0.0)
        assertEquals(1800.0, s.totalAnulaciones, 0.0)
        assertEquals(5, s.totalTransacciones)
        assertEquals(3, s.diasConVentas)
        assertEquals("19/10/2026", s.mejorDia); assertEquals(7200.0, s.maxVentas, 0.0)
        assertEquals("Público General", s.pasajeMasVendido); assertEquals(3, s.cantidadPasajeMasVendido)
        assertEquals((7200.0 + 4300.0) / 2, s.promedioLunSab, 0.0)   // lun y mar
        assertEquals(4300.0, s.promedioDomingos, 0.0)
        assertFalse(s.ventasPorTipo.containsKey("Cargo: Caja"))
    }

    @Test fun filtersByRangeButKeepsUnparsableDates() {
        val data = listOf(tx(1, "A", 100.0, dia = "21"), tx(2, "A", 999.0, dia = "01", mes = "08"), tx(3, "A", 50.0, dia = "xx"))
        val s = Statistics.sales(data, today, days = 30)
        assertEquals(150.0, s.totalVentas, 0.0)
    }

    @Test fun weeklyHasSevenDaysEndingToday() {
        val data = listOf(
            tx(1, "A", 100.0, dia = "21"), tx(2, "B", 50.0, dia = "21"),
            tx(3, "Anulación: A", -100.0, dia = "21"), tx(4, "C", 70.0, dia = "15"),
        )
        val w = Statistics.weekly(data, today)
        assertEquals(7, w.size)
        assertEquals(DaySales("Mié", "21/10", 150.0), w.last())
        assertEquals(DaySales("Jue", "15/10", 70.0), w.first())
        assertEquals(0.0, w[1].ventas, 0.0)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PendingTransactionServiceTest {
    @Test fun alertFiresEveryFiveMinutesUntilShown() = runTest {
        val store = FakeRegisterStore().apply { openTransactions = listOf(tx(1, "A", 1.0, dia = "01", mes = "01", ano = "2020")); openNextId = 2 }
        val svc = PendingTransactionService(CashRegister(store, FakeClock.at(2026, 10, 21), UTC))
        var alerts = 0
        svc.startPeriodicCheck(backgroundScope) { alerts++; svc.markAlertShown() }

        advanceTimeBy(PendingTransactionService.CHECK_INTERVAL_MS - 1); assertEquals(0, alerts)
        advanceTimeBy(2); assertEquals(1, alerts)
        advanceTimeBy(PendingTransactionService.CHECK_INTERVAL_MS); assertEquals(1, alerts) // ya mostrada

        svc.onAlertDismissed(backgroundScope)
        advanceTimeBy(PendingTransactionService.CHECK_INTERVAL_MS + 1)
        assertFalse(svc.hasShownAlert)
        svc.stop()
    }

    @Test fun nothingPendingMeansNoAlert() = runTest {
        val svc = PendingTransactionService(CashRegister(FakeRegisterStore(), FakeClock.at(2026, 10, 21), UTC))
        var alerts = 0
        svc.startPeriodicCheck(backgroundScope) { alerts++ }
        advanceTimeBy(PendingTransactionService.CHECK_INTERVAL_MS * 3)
        assertEquals(0, alerts)
        assertNull(null)
        svc.stop()
    }
}
