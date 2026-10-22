package cl.suray.posbus.domain

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportFilesTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) =
        Calendar.getInstance(utc).apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis

    @Test fun titleAndDate() {
        val f = PdfFileInfo("reporte_MIÉRCOLES_22_10_26(1).pdf", at(2026, 10, 21, 17, 6), 100)
        assertEquals("Miércoles 21/Oct", ReportFiles.title(f, utc))
        assertEquals("21/10/2026 - 17:06", ReportFiles.dateTime(f, utc))
    }

    @Test fun numberAndSize() {
        assertEquals("2", ReportFiles.number("reporte_X_1_1_26(2).pdf"))
        assertEquals("", ReportFiles.number("reporte_X.pdf"))
        assertEquals("500 B", ReportFiles.size(500))
        assertEquals("1.5 KB", ReportFiles.size(1536))
        assertEquals("2.0 MB", ReportFiles.size(2 * 1024 * 1024))
    }

    @Test fun sortAndFilter() {
        val a = PdfFileInfo("reporte_LUNES_1.pdf", 1, 1)
        val b = PdfFileInfo("reporte_MARTES_2.pdf", 2, 1)
        assertEquals(listOf(b, a), ReportFiles.sorted(listOf(a, b)))
        assertEquals(listOf(b), ReportFiles.filter(listOf(a, b), "martes"))
        assertEquals(listOf(a, b), ReportFiles.filter(listOf(a, b), "  "))
    }

    @Test fun statsCountOnlyReports() {
        val now = at(2026, 10, 22)
        val s = ReportFiles.stats(
            listOf(
                PdfFileInfo("reporte_A(1).pdf", now - 1000, 1024 * 1024),
                PdfFileInfo("reporte_B(1).pdf", now - 10L * 24 * 3600 * 1000, 1024 * 1024),
                PdfFileInfo("otro.pdf", now, 5_000_000),
            ),
            now,
        )
        assertEquals(2, s.total)
        assertEquals(1, s.lastWeek)
        assertEquals("2.00", s.totalSizeMb)
    }
}
