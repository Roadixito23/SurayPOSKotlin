package cl.suray.posbus.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Huella de venta usada por `ClosingService.merge` para no duplicar ventas al reintentar un cierre. */
class ClosingMergeTest {
    private val batch = listOf(
        tx(1, "Público General", 3600.0, timestamp = 1_000L),
        tx(2, "Cargo: Caja", 2500.0, timestamp = 2_000L),
        tx(3, "Anulación: Público General", -3600.0, timestamp = 3_000L),
    ).map { it.toEntity(7, 2026) }

    @Test fun mergingTheSameBatchTwiceAddsNothing() {
        val first = newTransactionsOnly(emptyList(), batch)
        assertEquals(batch, first)
        assertTrue(newTransactionsOnly(first, batch).isEmpty())

        val all = first + newTransactionsOnly(first, batch)
        assertEquals(3, all.size)
        assertEquals(2500.0, ClosingTotals.forMerge(all).grandTotal, 0.0)
    }

    @Test fun fingerprintIgnoresRoomIdsAndRestartedRegisterIds() {
        val stored = batch.mapIndexed { i, e -> e.copy(id = 100L + i) }
        val retried = batch.map { it.copy(transactionId = it.transactionId + 10) }
        assertTrue(newTransactionsOnly(stored, retried).isEmpty())
    }

    @Test fun reusedComprobanteWithDifferentTimestampIsANewSale() {
        // Tras anular, el contador retrocede y la venta siguiente reutiliza el número.
        val reused = tx(4, "Público General", 3600.0, comprobante = "01-000001", timestamp = 4_000L).toEntity(7, 2026)
        assertEquals(listOf(reused), newTransactionsOnly(batch, batch + reused))
    }

    @Test fun anyDifferingFieldMakesItNew() {
        val base = batch.first()
        val variants = listOf(
            base.copy(comprobante = "01-999999"),
            base.copy(nombre = "Adulto Mayor"),
            base.copy(valor = 1800.0),
            base.copy(timestamp = base.timestamp + 1),
        )
        assertEquals(variants, newTransactionsOnly(listOf(base), variants))
    }
}
