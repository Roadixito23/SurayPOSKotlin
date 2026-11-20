package cl.suray.posbus.domain

import cl.suray.posbus.data.prefs.AppPrefs
import cl.suray.posbus.data.prefs.LastTransaction
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SaleRecordingTest {
    private lateinit var sp: FakeSharedPreferences
    private lateinit var prefs: AppPrefs
    private lateinit var counter: ComprobanteCounter
    private lateinit var register: CashRegister
    private val clock = FakeClock.at(2026, 11, 20)

    @Before fun setUp() {
        sp = FakeSharedPreferences()
        prefs = AppPrefs(sp)
        counter = ComprobanteCounter(prefs)
        register = CashRegister(prefs, clock, UTC, batcher = prefs)
    }

    private fun sell(fail: Boolean = false) = recordSale(prefs, counter, register) { comprobante ->
        register.receiveData("Público General", 3600.0, comprobante)
        if (fail) error("boom")
        prefs.lastTransactionDate = "2026-11-20"
        LastTransaction("Público General", 3600.0, comprobante, false).also { prefs.lastTransaction = it }
    }

    @Test fun saleIsSavedWithASingleCommitAndNoApply() {
        val last = sell()
        assertEquals("01-000001", last.comprobante)
        assertEquals(1, sp.commits)
        assertEquals(0, sp.applies)
        // Todo quedó en disco, visto por una instancia nueva (como tras reiniciar la app).
        val reopened = AppPrefs(sp)
        assertEquals(1, reopened.comprobanteNumber)
        assertEquals(1, reopened.openTransactions.size)
        assertEquals(2, reopened.openNextId)
        assertEquals("2026-11-20", reopened.lastTransactionDate)
        assertEquals("01-000001", reopened.lastTransaction?.comprobante)
    }

    @Test fun failureInRecordLeavesNothingChanged() {
        sell() // una venta previa confirmada
        sp.commits = 0
        assertThrows(IllegalStateException::class.java) { sell(fail = true) }
        assertEquals(0, sp.commits)
        assertEquals(1, counter.number)
        assertEquals("01-000001", counter.formatted)
        assertEquals(1, counter.numberState.value)
        assertEquals(1, register.all.size)
        assertEquals(1, register.changes.value.size)
        val reopened = AppPrefs(sp)
        assertEquals(1, reopened.comprobanteNumber)
        assertEquals(1, reopened.openTransactions.size)
    }

    @Test fun failedCommitThrowsAndRevertsMemory() {
        sp.commitSucceeds = false
        assertThrows(IOException::class.java) { sell() }
        assertEquals(0, counter.number)
        assertTrue(register.all.isEmpty())
        assertEquals(0, AppPrefs(sp).comprobanteNumber)
        // Tras recuperarse el disco, la siguiente venta usa el número 1, sin saltos.
        sp.commitSucceeds = true
        assertEquals("01-000001", sell().comprobante)
    }

    @Test fun nestedBatchesCommitOnlyOnce() {
        prefs.batch {
            prefs.batch { prefs.comprobanteNumber = 5 }
            prefs.openNextId = 9
            assertEquals(0, sp.commits)
        }
        assertEquals(1, sp.commits)
        assertEquals(5, sp.data["comprobanteNumber"])
        assertEquals(9, sp.data["reporte_caja_next_id"])
    }

    @Test fun readsInsideABatchSeeItsOwnWrites() {
        prefs.batch {
            prefs.comprobanteNumber = 7
            assertEquals(7, prefs.comprobanteNumber)
            prefs.lastTransactionDate = "2026-11-20"
            assertEquals("2026-11-20", prefs.lastTransactionDate)
            prefs.lastTransactionDate = null
            assertEquals(null, prefs.lastTransactionDate)
        }
    }

    @Test fun exceptionInsideBatchWritesNothing() {
        assertThrows(IllegalArgumentException::class.java) {
            prefs.batch { prefs.comprobanteNumber = 3; throw IllegalArgumentException() }
        }
        assertEquals(0, sp.commits)
        assertEquals(0, prefs.comprobanteNumber)
        // y el lote queda cerrado: una escritura posterior vuelve a usar apply()
        prefs.comprobanteNumber = 4
        assertEquals(1, sp.applies)
    }

    @Test fun writesOutsideABatchStillUseApply() {
        prefs.comprobanteNumber = 2
        assertEquals(1, sp.applies)
        assertEquals(0, sp.commits)
    }

    @Test fun cancelLastSaleIsOneCommitAndRollsBackTheCounter() {
        sell(); sell()
        assertEquals(2, counter.number)
        sp.commits = 0
        assertTrue(cancelLastSale(prefs, counter, register))
        assertEquals(1, sp.commits)
        assertEquals(0, sp.applies)
        assertEquals(1, counter.number)
        val reopened = AppPrefs(sp)
        assertEquals(1, reopened.comprobanteNumber)
        assertEquals(3, reopened.openTransactions.size) // 2 ventas + la anulación
    }

    @Test fun cancelLastSaleWithNothingToCancelReturnsFalse() {
        assertFalse(cancelLastSale(prefs, counter, register))
        assertEquals(0, counter.number)
    }
}
