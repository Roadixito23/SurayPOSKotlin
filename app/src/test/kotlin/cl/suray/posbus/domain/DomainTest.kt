package cl.suray.posbus.domain

import cl.suray.posbus.data.db.TransactionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDayTest {
    @Test fun knownWeekdays() {
        assertEquals(4, LocalDay(1970, 1, 1).dayOfWeek)   // jueves
        assertEquals(3, LocalDay(2026, 10, 21).dayOfWeek) // miércoles
        assertEquals(7, LocalDay(2026, 10, 25).dayOfWeek) // domingo
        assertEquals(1, LocalDay(2024, 2, 26).dayOfWeek)  // lunes
    }

    @Test fun epochRoundTripAndLeapYears() {
        for (d in listOf(LocalDay(2024, 2, 29), LocalDay(2026, 12, 31), LocalDay(2000, 3, 1), LocalDay(1969, 12, 31))) {
            assertEquals(d, LocalDay.fromEpochDay(d.toEpochDay()))
        }
        assertEquals(LocalDay(2024, 2, 29), LocalDay(2024, 3, 1).minusDays(1))
        assertEquals(LocalDay(2025, 12, 31), LocalDay(2026, 1, 1).minusDays(1))
        assertEquals(366L, LocalDay(2024, 1, 1).daysUntil(LocalDay(2025, 1, 1)))
    }

    @Test fun parseAndIso() {
        assertEquals("2026-01-05", LocalDay(2026, 1, 5).iso())
        assertEquals(LocalDay(2026, 10, 21), LocalDay.parse("2026-10-21"))
        assertNull(LocalDay.parse("21/10/2026"))
        assertTrue(LocalDay(2026, 1, 4) < LocalDay(2026, 1, 5))
    }

    @Test fun dateParts() {
        val p = DateParts.of(FakeClock.at(2026, 3, 7).millis, UTC)
        assertEquals(DateParts("07", "03", "2026", "12:00"), p)
    }
}

class ComprobanteTest {
    @Test fun formatting() {
        assertEquals("01-000000", ComprobanteCounter(FakeComprobanteStore()).formatted)
        assertEquals("12-000345", ComprobanteCounter.format(12, 345))
    }

    @Test fun incrementWrapsToOneNotZero() {
        val store = FakeComprobanteStore(comprobanteNumber = 999_998)
        val c = ComprobanteCounter(store)
        c.increment(); assertEquals(999_999, store.comprobanteNumber)
        c.increment(); assertEquals(1, store.comprobanteNumber)
        assertEquals("01-000001", c.formatted)
    }

    @Test fun rollbackNeverGoesBelowOne() {
        val store = FakeComprobanteStore(comprobanteNumber = 3)
        val c = ComprobanteCounter(store)
        c.rollbackLast(); assertEquals(2, store.comprobanteNumber)
        c.rollbackLast(); assertEquals(1, store.comprobanteNumber)
        c.rollbackLast(); assertEquals(1, store.comprobanteNumber)
        assertEquals(1, c.numberState.value)
    }

    @Test fun numberStateFollowsChanges() {
        val c = ComprobanteCounter(FakeComprobanteStore(comprobanteNumber = 7))
        assertEquals(7, c.numberState.value)
        c.increment(); assertEquals(8, c.numberState.value)
        c.reset(); assertEquals(0, c.numberState.value)
    }

    @Test fun resetGoesToZero() {
        val store = FakeComprobanteStore(comprobanteNumber = 50)
        ComprobanteCounter(store).reset()
        assertEquals(0, store.comprobanteNumber)
    }
}

class CashRegisterTest {
    private fun register(store: FakeRegisterStore = FakeRegisterStore(), clock: FakeClock = FakeClock.at(2026, 10, 21)) =
        CashRegister(store, clock, UTC)

    @Test fun addAssignsIncrementalIdsAndPersists() {
        val store = FakeRegisterStore()
        val r = register(store)
        r.receiveData("Público General", 3600.0, "01-000001")
        r.receiveCargoData("Caja", 2500.0, "01-000002")
        r.addOffer(5000.0, "01-000003")
        assertEquals(listOf(1, 2, 3), r.all.map { it.id })
        assertEquals(listOf("Público General", "Cargo: Caja", "Oferta Ruta"), r.all.map { it.nombre })
        assertEquals(4, store.openNextId)
        assertEquals(3, store.openTransactions.size)
        assertEquals("21", r.all[0].dia); assertEquals("10", r.all[0].mes); assertEquals("2026", r.all[0].ano)
        assertEquals(11_100.0, r.total(), 0.0)
    }

    @Test fun cancelLastNegatesValueAndKeepsComprobante() {
        val r = register()
        r.receiveData("Adulto Mayor", 1800.0, "01-000001")
        assertTrue(r.cancelLast())
        val a = r.all.last()
        assertEquals("Anulación: Adulto Mayor", a.nombre)
        assertEquals(-1800.0, a.valor, 0.0)
        assertEquals("01-000001", a.comprobante)
        assertEquals(0.0, r.total(), 0.0)
    }

    @Test fun cancelLastSkipsExistingCancellations() {
        val r = register()
        r.receiveData("A", 100.0, "c1")
        r.receiveData("B", 200.0, "c2")
        r.cancelLast() // anula B
        r.cancelLast() // la última NO anulación sigue siendo B
        assertEquals(listOf("A", "B", "Anulación: B", "Anulación: B"), r.all.map { it.nombre })
    }

    @Test fun cancelOnEmptyDoesNothing() {
        assertFalse(register().cancelLast())
    }

    @Test fun clearResetsNextId() {
        val store = FakeRegisterStore()
        val r = register(store)
        r.receiveData("A", 1.0, "c")
        r.clear()
        assertTrue(r.all.isEmpty()); assertEquals(1, store.openNextId)
        r.receiveData("B", 1.0, "c")
        assertEquals(1, r.all.single().id)
    }

    @Test fun loadsExistingAndRecomputesMissingNextId() {
        val store = FakeRegisterStore().apply { openTransactions = listOf(tx(4, "A", 1.0), tx(9, "B", 1.0)) }
        val r = register(store)
        r.receiveData("C", 1.0, "c")
        assertEquals(10, r.all.last().id)
    }

    @Test fun removeAndOrdered() {
        val store = FakeRegisterStore().apply { openTransactions = listOf(tx(3, "C", 1.0), tx(1, "A", 1.0), tx(2, "B", 1.0)); openNextId = 4 }
        val r = register(store)
        assertEquals(listOf(1, 2, 3), r.ordered().map { it.id })
        r.remove(2)
        assertEquals(listOf(3, 1), r.all.map { it.id })
    }

    @Test fun pendingOldIgnoresTodayAndCancellations() {
        val today = LocalDay(2026, 10, 21)
        val store = FakeRegisterStore().apply {
            openTransactions = listOf(tx(1, "A", 100.0), tx(2, "Anulación: X", -5.0, dia = "10"))
        }
        val r = register(store)
        assertFalse(r.hasPendingOld(today))
        assertEquals(0, r.oldestPendingDays(today))

        store.openTransactions = listOf(tx(1, "A", 100.0, dia = "19"), tx(2, "B", 1.0, dia = "20"))
        val r2 = register(store)
        assertTrue(r2.hasPendingOld(today))
        assertEquals(2, r2.oldestPendingDays(today))
    }

    @Test fun pendingAcrossMonthsAndYears() {
        val today = LocalDay(2026, 1, 2)
        val store = FakeRegisterStore().apply { openTransactions = listOf(tx(1, "A", 1.0, dia = "30", mes = "12", ano = "2025")) }
        val r = register(store)
        assertTrue(r.hasPendingOld(today))
        assertEquals(3, r.oldestPendingDays(today))
    }

    @Test fun missingYearAssumesCurrentYear() {
        val store = FakeRegisterStore().apply { openTransactions = listOf(tx(1, "A", 1.0, dia = "20", ano = null)) }
        assertTrue(register(store).hasPendingOld(LocalDay(2026, 10, 21)))
        val byDate = register(store).byDate("20", "10", "2026")
        assertEquals(1, byDate.size)
    }
}

class ClassifierTest {
    @Test fun storedCategoryIsCaseSensitive() {
        assertEquals(TransactionCategory.ANULACION, Classifier.categoryOf("Anulación: X"))
        assertEquals(TransactionCategory.CORRESPONDENCIA, Classifier.categoryOf("Cargo: Juan"))
        assertEquals(TransactionCategory.PASAJE, Classifier.categoryOf("cargo: juan"))
        assertEquals(TransactionCategory.PASAJE, Classifier.categoryOf("anulación: x"))
        assertEquals(TransactionCategory.PASAJE, Classifier.categoryOf("Oferta Ruta"))
    }

    @Test fun autoCloseBucketIsCaseInsensitiveAndMoreLenient() {
        assertEquals(TransactionCategory.ANULACION, Classifier.autoCloseBucket("ANULACIÓN: X"))
        assertEquals(TransactionCategory.CORRESPONDENCIA, Classifier.autoCloseBucket("cargo: juan"))
        assertEquals(TransactionCategory.CORRESPONDENCIA, Classifier.autoCloseBucket("Envío correspondencia"))
        assertEquals(TransactionCategory.PASAJE, Classifier.autoCloseBucket("Público General"))
    }

    @Test fun autoCloseAndMergeTotalsDiverge() {
        val list = listOf(tx(1, "Público General", 3600.0), tx(2, "cargo: x", 1000.0), tx(3, "Anulación: Público General", -3600.0))
        val auto = ClosingTotals.forAutoClose(list)
        assertEquals(3600.0, auto.pasajes, 0.0); assertEquals(1000.0, auto.correspondencias, 0.0); assertEquals(-3600.0, auto.anulaciones, 0.0)
        assertEquals(1000.0, auto.grandTotal, 0.0)
        // "cargo: x" en minúscula cuenta como pasaje con la regla sensible a mayúsculas.
        val strict = ClosingTotals.fromOpen(list)
        assertEquals(4600.0, strict.pasajes, 0.0); assertEquals(0.0, strict.correspondencias, 0.0)
    }

    @Test fun mergeTotalsUseStoredCategory() {
        val entities = listOf(
            tx(1, "A", 100.0).toEntity(1, 2026),
            tx(2, "Cargo: B", 50.0).toEntity(1, 2026),
            tx(3, "Anulación: A", -100.0).toEntity(1, 2026),
        )
        assertTrue(entities[2].isCancellation)
        val t = ClosingTotals.forMerge(entities)
        assertEquals(Totals(100.0, 50.0, -100.0), t)
        assertEquals(50.0, t.grandTotal, 0.0)
    }

    @Test fun entityConversionFillsMissingYear() {
        val e = tx(1, "A", 1.0, ano = null).toEntity(7, 2025)
        assertEquals("2025", e.ano); assertEquals(7L, e.dailyClosingId); assertEquals("pasaje", e.category)
        assertEquals("2025", e.toOpen().ano)
    }
}

class NfcAuthTest {
    @Test fun uidIsUppercaseHexWithColons() {
        assertEquals("04:A2:3B:0C", NfcAuth.formatUid(byteArrayOf(0x04, 0xA2.toByte(), 0x3B, 0x0C)))
    }

    @Test fun emptyListAllowsAnyCard() {
        assertTrue(NfcAuth.isAuthorized(emptyList(), "04:A2"))
        assertTrue(NfcAuth.isAuthorized(listOf("04:A2"), "04:A2"))
        assertFalse(NfcAuth.isAuthorized(listOf("04:A2"), "FF:FF"))
    }
}

class CashRegisterObservableTest {
    @Test fun changesEmitOnEveryMutation() {
        val r = CashRegister(FakeRegisterStore(), FakeClock.at(2026, 10, 21), UTC)
        assertEquals(0, r.changes.value.size)
        r.receiveData("A", 1.0, "c"); assertEquals(1, r.changes.value.size)
        r.cancelLast(); assertEquals(2, r.changes.value.size)
        r.clear(); assertEquals(0, r.changes.value.size)
    }
}

class ReportDatesTest {
    private val nowMillis = FakeClock.at(2026, 10, 22).millis // jueves

    @Test fun sameDayUsesThatDayAndLastTransactionTime() {
        val list = listOf(tx(1, "A", 1.0, hora = "08:15"), tx(2, "B", 1.0, hora = "17:42"))
        assertEquals(ReportDate(LocalDay(2026, 10, 21), 17, 42), ReportDates.determine(list, nowMillis, UTC))
    }

    @Test fun mixedDaysFallBackToNow() {
        val list = listOf(tx(1, "A", 1.0, dia = "20"), tx(2, "B", 1.0, dia = "21"))
        assertEquals(ReportDate(LocalDay(2026, 10, 22), 12, 0), ReportDates.determine(list, nowMillis, UTC))
    }

    @Test fun firstNonCancellationDefinesTheDay() {
        val list = listOf(tx(1, "Anulación: X", -1.0, dia = "21"), tx(2, "B", 1.0, dia = "21", hora = "09:00"))
        assertEquals(LocalDay(2026, 10, 21), ReportDates.determine(list, nowMillis, UTC).day)
    }

    @Test fun emptyOrBrokenDatesFallBack() {
        assertEquals(LocalDay(2026, 10, 22), ReportDates.determine(emptyList(), nowMillis, UTC).day)
        assertEquals(LocalDay(2026, 10, 22), ReportDates.determine(listOf(tx(1, "A", 1.0, dia = "xx")), nowMillis, UTC).day)
    }

    @Test fun badTimeKeepsDefaultEndOfDay() {
        val d = ReportDates.determine(listOf(tx(1, "A", 1.0, hora = "sin hora")), nowMillis, UTC)
        assertEquals(23, d.hour); assertEquals(59, d.minute)
    }
}

class CardPaymentsTest {
    private class Store : CardPaymentStore {
        val data = HashMap<String, String>()
        override fun cardPayments(date: String) = data[date]
        override fun setCardPayments(date: String, value: String) { data[date] = value }
    }

    @Test fun addsAndTotalsToday() {
        val s = Store()
        val c = CardPayments(s, FakeClock.at(2026, 10, 21), UTC)
        assertEquals(0.0, c.totalToday(), 0.0)
        c.add(3600.0, "AB1", "2026-10-21T10:00"); c.add(1800.0, "AB2", "2026-10-21T10:05")
        assertEquals(5400.0, c.totalToday(), 0.0)
        assertTrue(s.data.containsKey("2026-10-21"))
        assertEquals(0.0, CardPayments(s, FakeClock.at(2026, 10, 22), UTC).totalToday(), 0.0)
    }

    @Test fun corruptStoreIsEmpty() {
        val s = Store().apply { data["2026-10-21"] = "{roto" }
        assertEquals(0.0, CardPayments(s, FakeClock.at(2026, 10, 21), UTC).totalToday(), 0.0)
    }
}

class CloseRegisterTest {
    private class Saver(var fail: Boolean = false) : ManualClosingSaver {
        var saved: Pair<List<cl.suray.posbus.data.prefs.OpenTransaction>, LocalDay>? = null
        override suspend fun saveManualClosing(
            transactions: List<cl.suray.posbus.data.prefs.OpenTransaction>, day: LocalDay, pdfFilename: String?,
        ): cl.suray.posbus.data.db.DailyClosing {
            if (fail) error("sin espacio")
            saved = transactions to day
            return cl.suray.posbus.data.db.DailyClosing(
                date = day.iso(), year = day.year, month = day.month, day = day.day, dayOfWeek = "MIÉRCOLES", createdAt = 1, closedAt = 1,
            )
        }
    }

    private class Printer(var fail: Boolean = false) : ReportPrinter {
        var printed: CashReport? = null
        override suspend fun printReport(report: CashReport) { if (fail) error("sin papel"); printed = report }
    }

    private fun setup(printer: Printer, saver: Saver): Pair<CashRegister, CloseRegister> {
        val clock = FakeClock.at(2026, 10, 21)
        val reg = CashRegister(FakeRegisterStore(), clock, UTC)
        reg.receiveData("Público General", 3600.0, "01-000001")
        reg.receiveData("Cargo: Caja", 2500.0, "01-000002")
        return reg to CloseRegister(reg, saver, { printer }, clock, UTC)
    }

    private fun <T> run(block: suspend () -> T): T = kotlinx.coroutines.runBlocking { block() }

    @Test fun emptyRegisterDoesNothing() {
        val clock = FakeClock.at(2026, 10, 21)
        val close = CloseRegister(CashRegister(FakeRegisterStore(), clock, UTC), Saver(), { Printer() }, clock, UTC)
        assertEquals(CloseResult.Empty, run { close() })
    }

    @Test fun successPrintsSavesThenClears() {
        val printer = Printer(); val saver = Saver()
        val (reg, close) = setup(printer, saver)
        assertTrue(run { close() } is CloseResult.Done)
        assertEquals(6100.0, printer.printed!!.total, 0.0)
        assertEquals(LocalDay(2026, 10, 21), saver.saved!!.second)
        assertEquals(2, saver.saved!!.first.size)
        assertFalse(reg.hasActive())
    }

    @Test fun printFailureKeepsTheRegisterAndSavesNothing() {
        val saver = Saver()
        val (reg, close) = setup(Printer(fail = true), saver)
        assertTrue(run { close() } is CloseResult.PrintFailed)
        assertEquals(2, reg.all.size)
        assertNull(saver.saved)
    }

    @Test fun saveFailureKeepsTheRegister() {
        val (reg, close) = setup(Printer(), Saver(fail = true))
        assertTrue(run { close() } is CloseResult.SaveFailed)
        assertEquals(2, reg.all.size)
    }
}
