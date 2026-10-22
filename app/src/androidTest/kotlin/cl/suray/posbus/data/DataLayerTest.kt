package cl.suray.posbus.data

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import cl.suray.posbus.data.db.AppDatabase
import cl.suray.posbus.data.db.DailyClosing
import cl.suray.posbus.data.db.MonthlyClosing
import cl.suray.posbus.data.db.TransactionEntity
import cl.suray.posbus.data.db.WeeklyClosing
import cl.suray.posbus.data.prefs.AppPrefs
import cl.suray.posbus.data.prefs.OpenTransaction
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DataLayerTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var prefs: AppPrefs

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val sp = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        sp.edit().clear().commit()
        prefs = AppPrefs(sp)
    }

    @After
    fun tearDown() {
        db.close()
        context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun closing(date: String, day: Int) = DailyClosing(
        date = date, year = 2026, month = 10, day = day, dayOfWeek = "MIÉRCOLES",
        createdAt = 1L, closedAt = 2L,
    )

    private fun tx(closingId: Long, id: Int, valor: Double, category: String) = TransactionEntity(
        dailyClosingId = closingId, transactionId = id, nombre = "Público General", valor = valor,
        comprobante = "01-%06d".format(id), dia = "21", mes = "10", ano = "2026", hora = "10:00",
        timestamp = id.toLong(), category = category, isCancellation = valor < 0,
    )

    // --- Room ---------------------------------------------------------------------------------

    @Test
    fun closingRoundTripAndUniqueDate() = runBlocking {
        val id = db.dailyClosingDao().insert(closing("2026-10-21", 21))
        assertEquals("2026-10-21", db.dailyClosingDao().getById(id)?.date)
        assertNotNull(db.dailyClosingDao().getByDate("2026-10-21"))
        assertNull(db.dailyClosingDao().getByDate("2026-10-22"))
        val dup = runCatching { db.dailyClosingDao().insert(closing("2026-10-21", 21)) }
        assertTrue("la fecha debe ser única", dup.isFailure)
    }

    @Test
    fun transactionsDeletedInCascade() = runBlocking {
        val id = db.dailyClosingDao().insert(closing("2026-10-21", 21))
        db.transactionDao().insertAll(listOf(tx(id, 1, 3600.0, "pasaje"), tx(id, 2, -3600.0, "anulacion")))
        assertEquals(2, db.transactionDao().count(id))
        db.dailyClosingDao().delete(id)
        assertEquals(0, db.transactionDao().count(id))
    }

    @Test
    fun totalsByCategoryAndOrdering() = runBlocking {
        val id = db.dailyClosingDao().insert(closing("2026-10-21", 21))
        db.transactionDao().insertAll(
            listOf(
                tx(id, 3, 1800.0, "pasaje"),
                tx(id, 1, 3600.0, "pasaje"),
                tx(id, 2, 5000.0, "correspondencia"),
            ),
        )
        val totals = db.transactionDao().totalsByCategory(id).associate { it.category to it.total }
        assertEquals(5400.0, totals["pasaje"]!!, 0.0)
        assertEquals(5000.0, totals["correspondencia"]!!, 0.0)
        assertEquals(listOf(1, 2, 3), db.transactionDao().getByClosingId(id).map { it.transactionId })
    }

    @Test
    fun rangeQueryAndWeeklyMonthlyUniqueness() = runBlocking {
        listOf(19, 20, 21, 22).forEach { db.dailyClosingDao().insert(closing("2026-10-$it", it)) }
        assertEquals(2, db.dailyClosingDao().getInRange("2026-10-20", "2026-10-21").size)

        val week = WeeklyClosing(year = 2026, weekNumber = 43, startDate = "2026-10-19", endDate = "2026-10-25", createdAt = 1L)
        db.weeklyClosingDao().insert(week)
        assertNotNull(db.weeklyClosingDao().getByYearWeek(2026, 43))
        assertTrue(runCatching { db.weeklyClosingDao().insert(week) }.isFailure)

        val month = MonthlyClosing(year = 2026, month = 10, monthName = "Octubre", startDate = "2026-10-01", endDate = "2026-10-31", createdAt = 1L)
        db.monthlyClosingDao().insert(month)
        assertNotNull(db.monthlyClosingDao().getByYearMonth(2026, 10))
        assertTrue(runCatching { db.monthlyClosingDao().insert(month) }.isFailure)
    }

    // --- Preferencias -------------------------------------------------------------------------

    @Test
    fun defaultFaresMatchFlutter() {
        assertEquals(6, prefs.weekdayFares.size)
        assertEquals("Int. hasta 15 Km", prefs.weekdayFares[3].nombre)
        assertEquals(3600.0, prefs.weekdayFares[0].precio, 0.0)
        assertEquals(4300.0, prefs.sundayFares[0].precio, 0.0)
        assertEquals(1300.0, prefs.sundayFares[5].precio, 0.0)
    }

    @Test
    fun readsFlutterFormattedJson() {
        context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE).edit()
            .putString(
                AppPrefs.KEY_OPEN_TRANSACTIONS,
                """[{"id":7,"nombre":"Cargo: Caja","valor":2500.0,"comprobante":"01-000010","dia":"21","mes":"10","ano":"2026","hora":"09:05","timestamp":1792608746934}]""",
            )
            .putString(AppPrefs.KEY_PASAJES, """[{"nombre":"Público General","precio":3900}]""")
            .commit()
        val t = prefs.openTransactions.single()
        assertEquals(7, t.id)
        assertEquals(1792608746934L, t.timestamp)
        assertEquals(3900.0, prefs.weekdayFares.single().precio, 0.0)
    }

    @Test
    fun openTransactionsRoundTripAndCorruptJsonFallsBack() {
        val list = listOf(OpenTransaction(1, "Adulto Mayor", 1800.0, "01-000001", "21", "10", "2026", "10:00", 5L))
        prefs.openTransactions = list
        prefs.openNextId = 2
        assertEquals(list, prefs.openTransactions)
        assertEquals(2, prefs.openNextId)

        context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE).edit()
            .putString(AppPrefs.KEY_OPEN_TRANSACTIONS, "{no es json").commit()
        assertTrue(prefs.openTransactions.isEmpty())
    }

    @Test
    fun cardPaymentsOlderThanTodayAreCleared() {
        prefs.setCardPayments("2026-10-19", "[]")
        prefs.setCardPayments("2026-10-21", "[1]")
        prefs.clearCardPaymentsBefore("2026-10-21")
        assertNull(prefs.cardPayments("2026-10-19"))
        assertEquals("[1]", prefs.cardPayments("2026-10-21"))
    }

    @Test
    fun counterDefaults() {
        assertEquals(0, prefs.comprobanteNumber)
        assertEquals(1, prefs.ticketId)
        assertEquals(1, prefs.openNextId)
    }

    @Test
    fun securityPrefsPersistLockoutAndPasswords() {
        val manager = cl.suray.posbus.domain.PasswordManager(prefs)
        assertTrue(manager.verifyLogin("123456"))
        manager.seedDefaults()
        assertEquals("654321", prefs.settingsPassword)
        assertTrue(manager.setPassword("246810"))
        assertEquals("246810", AppPrefs(context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)).password)

        var now = 5_000_000L
        val policy = cl.suray.posbus.domain.LockoutPolicy(prefs) { now }
        repeat(3) { policy.onFailure() }
        val reopened = cl.suray.posbus.domain.LockoutPolicy(
            AppPrefs(context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)),
        ) { now + 2_000 }
        assertEquals(3, reopened.remainingSeconds())
        policy.onSuccess()
        assertEquals(0L, prefs.lastLockTime)
        assertEquals(0, prefs.failedAttempts)
    }
}
