package cl.suray.posbus.domain

import cl.suray.posbus.data.prefs.OpenTransaction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ReceiptLayoutsTest {
    private fun tx(id: Int, nombre: String, valor: Double) =
        OpenTransaction(id, nombre, valor, "01-%06d".format(id), "21", "10", "2026", "10:%02d".format(id), 0L)

    private fun layout(vararg t: OpenTransaction) = ReportLayouts.build(
        CashReport(t.toList(), t.sumOf { it.valor }, ReportDate(LocalDay(2026, 10, 21), 14, 5)), terminalId = 3,
    )

    @Test fun reportKeepsEveryLineWithinThirtyTwoColumns() {
        val lines = ReceiptLayouts.report(
            layout(tx(1, "Público General", 3600.0), tx(2, "Int. hasta 15 Km", 1800.0), tx(3, "Anulación: Público General", -3600.0)),
            endMarginPt = 69f,
        )
        for (l in lines) when (l) {
            is ReceiptLine.Text -> if (l.size == ReceiptLayouts.BASE_SIZE) assertTrue("«${l.text}»", l.text.length <= ReceiptLayouts.COLUMNS)
            is ReceiptLine.Columns -> {
                assertTrue(l.widths.sum() <= ReceiptLayouts.COLUMNS)
                l.cells.forEachIndexed { i, c -> assertTrue("celda «$c»", c.length <= l.widths[i]) }
            }
            is ReceiptLine.Feed -> assertTrue(l.lines >= 1)
        }
    }

    @Test fun reportContentMatchesThePdfLayout() {
        val lines = ReceiptLayouts.report(layout(tx(1, "Público General", 3600.0), tx(2, "Cargo: Juan", 5000.0)), 69f)
        val texts = lines.filterIsInstance<ReceiptLine.Text>().map { it.text.trim() }
        assertTrue("Transportes Suray Limitada" in texts)
        assertTrue("CIERRE DE CAJA" in texts)
        assertTrue("MIÉRCOLES" in texts)
        assertTrue("Total de Ingresos: \$8.600" in texts)
        assertTrue("Pasajeros" in texts && "Otros" in texts)
        assertTrue("Sin anulaciones el día de Hoy" in texts)
        val cols = lines.filterIsInstance<ReceiptLine.Columns>()
        assertEquals(listOf("N°C", "Día", "Hora", "Tipo", "Valor"), cols.first { it.bold }.cells)
        assertEquals(listOf("1", "21", "10:01", "PG", "\$3.600"), cols.first { !it.bold && it.cells.size == 5 }.cells)
        assertEquals(listOf("ID: 3", "21/10/2026 14:05"), cols.first { it.cells.size == 2 }.cells)
    }

    @Test fun cancellationsShowSignAndSkipTheNoCancellationsNote() {
        val lines = ReceiptLayouts.report(layout(tx(1, "Público General", 3600.0), tx(2, "Anulación: Público General", -3600.0)), 69f)
        val texts = lines.filterIsInstance<ReceiptLine.Text>().map { it.text.trim() }
        assertTrue("Anulaciones" in texts)
        assertTrue("Total: -\$3.600" in texts)
        assertFalse("Sin anulaciones el día de Hoy" in texts)
    }

    @Test fun emptyPasajerosStillShown() {
        val texts = ReceiptLayouts.report(layout(tx(1, "Cargo: X", 100.0)), 69f).filterIsInstance<ReceiptLine.Text>().map { it.text }
        assertTrue("No hay transacciones" in texts)
    }

    @Test fun longCellsAreTruncatedToTheirColumn() {
        val c = ReceiptLayouts.fit(ReceiptLine.Columns(listOf("1234567890", "x", "x", "Escolar Intermedio", "\$1.234.567"), listOf(6, 3, 6, 7, 10), List(5) { Align.Left }))
        assertEquals(listOf("123456", "x", "x", "Escolar", "\$1.234.567"), c.cells)
    }

    @Test fun marginAndPadding() {
        assertEquals(9, ReceiptLayouts.marginLines(69f))   // 69 pt ≈ 24 mm ≈ 8.1 líneas de 3 mm
        assertEquals(1, ReceiptLayouts.marginLines(0f))
        assertEquals("   AB   ", ReceiptLayouts.centerPad("AB", 8))
        assertEquals("ABCD", ReceiptLayouts.centerPad("ABCDEF", 4))
    }

    @Test fun printerStateTexts() {
        assertTrue(PrinterState.isReady(1))
        assertFalse(PrinterState.isReady(4))
        assertEquals("sin papel o con la tapa abierta", PrinterState.describe(4))
        assertEquals("en estado desconocido (99)", PrinterState.describe(99))
    }
}

class ReceiptEngineTest {
    private class FakeDevice(
        var state: Int = 1,
        var commit: CommitResult = CommitResult(true, 0, "ok"),
        var failOnText: Boolean = false,
    ) : ReceiptDevice {
        val calls = mutableListOf<String>()
        override suspend fun state(): Int { calls += "state"; return state }
        override suspend fun begin() { calls += "begin" }
        override fun text(line: ReceiptLine.Text) { if (failOnText) error("binder muerto"); calls += "text:${line.text.trim()}" }
        override fun columns(line: ReceiptLine.Columns) { calls += "cols:${line.cells.joinToString("|")}" }
        override fun feed(lines: Int) { calls += "feed:$lines" }
        override suspend fun commit(): CommitResult { calls += "commit"; return commit }
        override fun discard() { calls += "discard" }
    }

    private val lines = listOf(
        ReceiptLine.Text("Hola"),
        ReceiptLine.Columns(listOf("a", "b"), listOf(10, 10), listOf(Align.Left, Align.Right)),
        ReceiptLine.Feed(2),
    )

    @Test fun happyPathChecksStateFirstAndCommitsLast() {
        val d = FakeDevice()
        runBlocking { ReceiptEngine(d).print(lines) }
        assertEquals(listOf("state", "begin", "text:Hola", "cols:a|b", "feed:2", "commit"), d.calls)
    }

    @Test fun notReadyPrinterNeverStartsAJob() {
        val d = FakeDevice(state = 4)
        try { runBlocking { ReceiptEngine(d).print(lines) }; fail() } catch (e: PrintFailure) {
            assertEquals(4, e.code)
            assertTrue(e.message!!.contains("sin papel"))
        }
        assertEquals(listOf("state"), d.calls)
    }

    @Test fun failedConfirmationDiscardsAndReports() {
        val d = FakeDevice(commit = CommitResult(false, 7, "Paper out"))
        try { runBlocking { ReceiptEngine(d).print(lines) }; fail() } catch (e: PrintFailure) {
            assertEquals(7, e.code)
            assertEquals("Paper out", e.message)
        }
        assertEquals("discard", d.calls.last())
    }

    @Test fun errorWhileSendingDiscardsTheJob() {
        val d = FakeDevice(failOnText = true)
        try { runBlocking { ReceiptEngine(d).print(lines) }; fail() } catch (e: PrintFailure) {
            assertTrue(e.message!!.startsWith("Error al enviar"))
        }
        assertEquals("discard", d.calls.last())
        assertFalse("commit" in d.calls)
    }
}
