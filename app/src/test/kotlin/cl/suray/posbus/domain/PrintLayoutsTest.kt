package cl.suray.posbus.domain

import cl.suray.posbus.data.prefs.OpenTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintLayoutsTest {
    private fun tx(id: Int, nombre: String, valor: Double, comprobante: String = "01-%06d".format(id)) =
        OpenTransaction(id, nombre, valor, comprobante, "21", "10", "2026", "10:%02d".format(id), 0L)

    private fun report(vararg t: OpenTransaction) = CashReport(
        t.toList(), t.sumOf { it.valor }, ReportDate(LocalDay(2026, 10, 21), 14, 5),
    )

    @Test fun clpFormatting() {
        assertEquals("0", formatClp(0.0))
        assertEquals("900", formatClp(900.0))
        assertEquals("3.600", formatClp(3600.0))
        assertEquals("1.234.567", formatClp(1234567.0))
        assertEquals("-2.500", formatClp(-2500.0))
    }

    @Test fun ticketTexts() {
        val t = TicketLayouts.regular("Público General", 3600.0, "01-000123", false, 0L, reprint = false)
        assertEquals("N° 01-000123", t.numberLine)
        assertEquals("TARIFA LUNES A SÁBADO", t.tariffLine)
        assertEquals("Valor: \$3.600", t.valueLine)
        assertFalse(t.isReprint)
        val r = TicketLayouts.regular("Adulto Mayor", 2150.0, "01-000001", true, 0L, reprint = true)
        assertEquals("TARIFA DOMINGO/FERIADO", r.tariffLine)
        assertNotNull(r.reprintedAt)
    }

    @Test fun sectionsAndTotals() {
        val l = ReportLayouts.build(
            report(
                tx(1, "Público General", 3600.0),
                tx(2, "Int. hasta 15 Km", 1800.0),
                tx(3, "Escolar Intermedio", 1000.0),
                tx(4, "Cargo: Juan", 5000.0),
                tx(5, "Anulación: Público General", -3600.0),
            ),
            terminalId = 7,
        )
        assertEquals(listOf("Pasajeros", "Intermedio", "Otros", "Anulaciones"), l.sections.map { it.title })
        assertEquals("Total: \$2.800", l.sections[1].totalLine)
        assertEquals("Total: -\$3.600", l.sections[3].totalLine)
        assertEquals("-\$3.600", l.sections[3].rows[0].valor)
        assertEquals("Anula", l.sections[3].rows[0].tipo)
        assertEquals("Total de Ingresos: \$7.800", l.totalLine)
        assertEquals("MIÉRCOLES", l.dayName)
        assertEquals("21/10/2026 14:05", l.dateTime)
        assertTrue(l.hasCancellations)
    }

    @Test fun emptyOptionalSectionsAreOmittedButPasajerosStays() {
        val l = ReportLayouts.build(report(tx(1, "Cargo: X", 100.0)), 1)
        assertEquals(listOf("Pasajeros", "Otros"), l.sections.map { it.title })
        assertTrue(l.sections[0].rows.isEmpty())
        assertFalse(l.hasCancellations)
    }

    @Test fun abbreviationsFollowFlutterRules() {
        val a = emptyMap<String, String>()
        assertEquals("PG", ReportLayouts.abbreviation("Público General", a))
        assertEquals("Esc.", ReportLayouts.abbreviation("Escolar General", a))
        assertEquals("Int.E", ReportLayouts.abbreviation("Escolar Intermedio", a))
        assertEquals("Int.15", ReportLayouts.abbreviation("Int. hasta 15 Km", a))
        assertEquals("Int.50", ReportLayouts.abbreviation("Int. hasta 50 Km", a))
        assertEquals("Cargo", ReportLayouts.abbreviation("Cargo: Pedro", a))
        assertEquals("XX", ReportLayouts.abbreviation("Escolar Intermedio", mapOf("Escolar Intermedio" to "XX")))
        assertEquals("Otra cosa", ReportLayouts.abbreviation("Otra cosa", a))
    }

    @Test fun comprobanteShortening() {
        assertEquals("123", ReportLayouts.shortComprobante("01-000123"))
        assertEquals("0", ReportLayouts.shortComprobante("01-000000"))
        assertEquals("AB-1", ReportLayouts.shortComprobante("AB-1"))
    }

    @Test fun rowsSortedByIdAndNullCheck() {
        val l = ReportLayouts.build(report(tx(2, "Adulto Mayor", 1800.0), tx(1, "Público General", 3600.0)), 1)
        assertEquals(listOf("1", "2"), l.sections[0].rows.map { it.comprobante })
        assertNull(l.sections.firstOrNull { it.title == "Anulaciones" })
    }
}
