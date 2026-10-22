package cl.suray.posbus.domain

import java.util.Locale
import java.util.TimeZone

/** PDF de un cierre guardado en el equipo (`reporte_DIA_d_m_yy(n).pdf`). */
data class PdfFileInfo(val name: String, val modifiedMillis: Long, val sizeBytes: Long)

data class RecoveryStats(val total: Int, val lastWeek: Int, val totalSizeMb: String)

/** Presentación y búsqueda de los reportes guardados (pantalla "Recuperación de Reportes"). */
object ReportFiles {
    private val MONTHS_ES = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
    private val DAYS_ES_TITLE = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

    /** Más reciente primero. */
    fun sorted(files: List<PdfFileInfo>) = files.sortedByDescending { it.modifiedMillis }

    fun filter(files: List<PdfFileInfo>, query: String): List<PdfFileInfo> =
        if (query.isBlank()) files else files.filter { it.name.contains(query.trim(), ignoreCase = true) }

    /** `Miércoles 22/Oct` según la fecha de modificación. */
    fun title(f: PdfFileInfo, tz: TimeZone = TimeZone.getDefault()): String {
        val d = LocalDay.of(f.modifiedMillis, tz)
        return "${DAYS_ES_TITLE[d.dayOfWeek - 1]} ${d.day}/${MONTHS_ES[d.month - 1]}"
    }

    /** `22/10/2026 - 17:06` */
    fun dateTime(f: PdfFileInfo, tz: TimeZone = TimeZone.getDefault()): String {
        val d = LocalDay.of(f.modifiedMillis, tz)
        val t = DateParts.of(f.modifiedMillis, tz).hora
        return "%02d/%02d/%04d - %s".format(d.day, d.month, d.year, t)
    }

    /** Número entre paréntesis del nombre (`(2)`), o vacío si no tiene. */
    fun number(name: String): String = Regex("\\((\\d+)\\)").find(name)?.groupValues?.get(1) ?: ""

    fun size(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }

    /** Solo cuenta los archivos `reporte_*.pdf`, como la pantalla original. */
    fun stats(files: List<PdfFileInfo>, nowMillis: Long): RecoveryStats {
        val reports = files.filter { it.name.startsWith("reporte_") && it.name.endsWith(".pdf") }
        return RecoveryStats(
            total = reports.size,
            lastWeek = reports.count { it.modifiedMillis > nowMillis - WEEK_MS },
            totalSizeMb = String.format(Locale.US, "%.2f", reports.sumOf { it.sizeBytes } / (1024.0 * 1024.0)),
        )
    }
}
