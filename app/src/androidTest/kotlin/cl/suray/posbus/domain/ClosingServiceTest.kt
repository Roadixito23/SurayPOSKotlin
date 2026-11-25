package cl.suray.posbus.domain

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import cl.suray.posbus.data.db.AppDatabase
import cl.suray.posbus.data.prefs.OpenTransaction
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClosingServiceTest {
    private class Store : OpenRegisterStore, LastDateStore {
        override var openTransactions: List<OpenTransaction> = emptyList()
        override var openNextId: Int = 1
        override var lastTransactionDate: String? = null
    }

    private val utc = TimeZone.getTimeZone("UTC")
    private lateinit var db: AppDatabase
    private lateinit var store: Store
    private lateinit var clock: Clock
    private var now = 0L

    @Before
    fun setUp() {
        val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        store = Store()
        setDay(LocalDay(2026, 10, 21))
        clock = Clock { now }
    }

    @After
    fun tearDown() = db.close()

    private fun setDay(d: LocalDay) { now = d.toEpochDay() * 86_400_000L + 12 * 3_600_000L }

    private fun service(register: CashRegister) = ClosingService(db, store, register, clock, utc)

    private fun line(id: Int, nombre: String, valor: Double, dia: String = "20") =
        OpenTransaction(id, nombre, valor, "01-%06d".format(id), dia, "10", "2026", "10:00", id.toLong())

    @Test
    fun sameDayOrNoPreviousDateDoesNothing() = runBlocking {
        val register = CashRegister(store, clock, utc)
        register.receiveData("A", 100.0, "c")
        val s = service(register)
        assertEquals(AutoCloseResult.NotNeeded, s.checkAndAutoClose())             // sin fecha previa
        store.lastTransactionDate = "2026-10-21"
        assertEquals(AutoCloseResult.NotNeeded, s.checkAndAutoClose())             // mismo día
        assertTrue(register.hasActive())
    }

    @Test
    fun emptyRegisterOnlyUpdatesDate() = runBlocking {
        store.lastTransactionDate = "2026-10-20"
        assertEquals(AutoCloseResult.NotNeeded, service(CashRegister(store, clock, utc)).checkAndAutoClose())
        assertEquals("2026-10-21", store.lastTransactionDate)
    }

    @Test
    fun dayChangeClosesPreviousDayAndClearsRegister() = runBlocking {
        store.openTransactions = listOf(
            line(1, "Público General", 3600.0), line(2, "Cargo: Caja", 2500.0), line(3, "Anulación: Público General", -3600.0),
        )
        store.openNextId = 4
        store.lastTransactionDate = "2026-10-20"
        val register = CashRegister(store, clock, utc)
        val s = service(register)
        assertTrue(s.shouldShowDayChangeAlert())

        assertEquals(AutoCloseResult.Closed, s.checkAndAutoClose())

        val closing = db.dailyClosingDao().getByDate("2026-10-20")!!
        assertEquals("MARTES", closing.dayOfWeek)
        assertEquals(3600.0, closing.totalPasajes, 0.0)
        assertEquals(2500.0, closing.totalCorrespondencias, 0.0)
        assertEquals(-3600.0, closing.totalAnulaciones, 0.0)
        assertEquals(2500.0, closing.grandTotal, 0.0)
        assertEquals(3, closing.transactionCount)
        assertEquals(3, db.transactionDao().count(closing.id))
        assertFalse(register.hasActive())
        assertEquals(1, store.openNextId)
        assertEquals("2026-10-21", store.lastTransactionDate)
        assertFalse(s.shouldShowDayChangeAlert())
    }

    @Test
    fun closingAnExistingDateMergesInsteadOfDuplicating() = runBlocking {
        store.lastTransactionDate = "2026-10-20"
        store.openTransactions = listOf(line(1, "Público General", 3600.0))
        store.openNextId = 2
        assertEquals(AutoCloseResult.Closed, service(CashRegister(store, clock, utc)).checkAndAutoClose())
        val first = db.dailyClosingDao().getByDate("2026-10-20")!!

        // Segunda caja del mismo día cerrada tras otro cambio de fecha.
        store.lastTransactionDate = "2026-10-20"
        store.openTransactions = listOf(line(1, "Cargo: Juan", 1000.0), line(2, "Anulación: Cargo: Juan", -1000.0))
        store.openNextId = 3
        assertEquals(AutoCloseResult.Closed, service(CashRegister(store, clock, utc)).checkAndAutoClose())

        assertEquals(1, db.dailyClosingDao().getAll().size)
        val merged = db.dailyClosingDao().getById(first.id)!!
        assertEquals(3, merged.transactionCount)
        assertEquals(3, db.transactionDao().count(first.id))
        assertEquals(3600.0, merged.totalPasajes, 0.0)
        assertEquals(1000.0, merged.totalCorrespondencias, 0.0)
        assertEquals(-1000.0, merged.totalAnulaciones, 0.0)
        assertEquals(3600.0, merged.grandTotal, 0.0)
        assertNotNull(merged.closedAt)
    }

    @Test
    fun mergingTheSameTransactionsTwiceDoesNotDuplicate() = runBlocking {
        val day = LocalDay(2026, 10, 20)
        val lines = listOf(
            line(1, "Público General", 3600.0), line(2, "Cargo: Caja", 2500.0), line(3, "Anulación: Público General", -3600.0),
        )
        val s = service(CashRegister(store, clock, utc))
        val created = s.saveManualClosing(lines, day)

        // Simula que el proceso murió tras guardar en Room y antes de vaciar la caja: se reintenta igual.
        val again = s.merge(created, lines)
        val twice = s.saveManualClosing(lines, day)

        assertEquals(created, again)
        assertEquals(created, twice)
        assertEquals(1, db.dailyClosingDao().getAll().size)
        val stored = db.dailyClosingDao().getById(created.id)!!
        assertEquals(created, stored)
        assertEquals(3, stored.transactionCount)
        assertEquals(3, db.transactionDao().count(created.id))
        assertEquals(3600.0, stored.totalPasajes, 0.0)
        assertEquals(2500.0, stored.totalCorrespondencias, 0.0)
        assertEquals(-3600.0, stored.totalAnulaciones, 0.0)
        assertEquals(2500.0, stored.grandTotal, 0.0)

        // Una venta nueva junto a las repetidas sí se agrega, una sola vez.
        val extra = line(4, "Adulto Mayor", 1800.0)
        val merged = s.merge(stored, lines + extra)
        assertEquals(4, merged.transactionCount)
        assertEquals(4, db.transactionDao().count(created.id))
        assertEquals(5400.0, merged.totalPasajes, 0.0)
        assertEquals(4300.0, merged.grandTotal, 0.0)
    }

    @Test
    fun autoCloseRetriedAfterCrashDoesNotDuplicate() = runBlocking {
        val lines = listOf(line(1, "Público General", 3600.0), line(2, "Cargo: Caja", 2500.0))
        store.openTransactions = lines
        store.openNextId = 3
        store.lastTransactionDate = "2026-10-20"
        assertEquals(AutoCloseResult.Closed, service(CashRegister(store, clock, utc)).checkAndAutoClose())

        // La caja no llegó a vaciarse: al reiniciar vuelven las mismas ventas y la fecha anterior.
        store.openTransactions = lines
        store.openNextId = 3
        store.lastTransactionDate = "2026-10-20"
        assertEquals(AutoCloseResult.Closed, service(CashRegister(store, clock, utc)).checkAndAutoClose())

        val closing = db.dailyClosingDao().getByDate("2026-10-20")!!
        assertEquals(2, closing.transactionCount)
        assertEquals(2, db.transactionDao().count(closing.id))
        assertEquals(6100.0, closing.grandTotal, 0.0)
    }

    @Test
    fun statisticsRepositoryJoinsClosedAndOpen() = runBlocking {
        store.lastTransactionDate = "2026-10-20"
        store.openTransactions = listOf(line(1, "A", 100.0))
        store.openNextId = 2
        service(CashRegister(store, clock, utc)).checkAndAutoClose()
        val register = CashRegister(store, clock, utc)
        register.receiveData("B", 50.0, "c")
        val all = StatisticsRepository(db, register).allTransactions()
        assertEquals(listOf("A", "B"), all.map { it.nombre })
    }

    private class OkPrinter : ReportPrinter {
        var last: CashReport? = null
        override suspend fun printReport(report: CashReport) { last = report }
    }

    private fun closer(register: CashRegister, printer: ReportPrinter) =
        CloseRegister(register, service(register), { printer }, clock, utc)

    @Test
    fun manualCloseSavesToRoomThenClearsRegister() = runBlocking {
        val register = CashRegister(store, clock, utc)
        register.receiveData("Público General", 3600.0, "01-000001")
        register.receiveCargoData("Caja", 2500.0, "01-000002")
        val printer = OkPrinter()

        val result = closer(register, printer)()

        assertTrue(result is CloseResult.Done)
        assertEquals(6100.0, printer.last!!.total, 0.0)
        val closing = db.dailyClosingDao().getByDate("2026-10-21")!!
        assertEquals(3600.0, closing.totalPasajes, 0.0)
        assertEquals(2500.0, closing.totalCorrespondencias, 0.0)
        assertEquals(6100.0, closing.grandTotal, 0.0)
        assertEquals(2, db.transactionDao().count(closing.id))
        assertFalse(register.hasActive())
    }

    @Test
    fun secondManualCloseSameDayMerges() = runBlocking {
        val register = CashRegister(store, clock, utc)
        register.receiveData("Público General", 3600.0, "01-000001")
        closer(register, OkPrinter())()

        register.receiveData("Adulto Mayor", 1800.0, "01-000002")
        register.cancelLast()
        assertTrue(closer(register, OkPrinter())() is CloseResult.Done)

        assertEquals(1, db.dailyClosingDao().getAll().size)
        val merged = db.dailyClosingDao().getByDate("2026-10-21")!!
        assertEquals(3, merged.transactionCount)
        assertEquals(5400.0, merged.totalPasajes, 0.0)
        assertEquals(-1800.0, merged.totalAnulaciones, 0.0)
        assertEquals(3600.0, merged.grandTotal, 0.0)
    }

    @Test
    fun failedPrintLeavesRoomAndRegisterUntouched() = runBlocking {
        val register = CashRegister(store, clock, utc)
        register.receiveData("Público General", 3600.0, "01-000001")
        val result = closer(register, UnavailableReportPrinter)()
        assertTrue(result is CloseResult.PrintFailed)
        assertTrue(register.hasActive())
        assertTrue(db.dailyClosingDao().getAll().isEmpty())
    }
}
