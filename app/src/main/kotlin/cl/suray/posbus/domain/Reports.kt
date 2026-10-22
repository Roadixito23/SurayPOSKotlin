package cl.suray.posbus.domain

import cl.suray.posbus.data.db.DailyClosing
import cl.suray.posbus.data.prefs.OpenTransaction
import java.util.TimeZone

/** Fecha y hora con que se rotula un reporte de caja. */
data class ReportDate(val day: LocalDay, val hour: Int, val minute: Int)

data class CashReport(val transactions: List<OpenTransaction>, val total: Double, val date: ReportDate)

/** Impresión del reporte de caja (PDF 58 mm); la implementación real llega con las integraciones. */
interface ReportPrinter {
    /** Debe lanzar excepción si no se pudo imprimir: en ese caso la caja no se vacía. */
    suspend fun printReport(report: CashReport)
}

object UnavailableReportPrinter : ReportPrinter {
    override suspend fun printReport(report: CashReport) {
        throw UnsupportedOperationException("Impresión aún no implementada")
    }
}

object ReportDates {
    /**
     * Si todas las ventas son del mismo día (la primera que no sea anulación marca el día),
     * el reporte lleva esa fecha y la hora de la última venta; si no, la fecha actual.
     * Así un cierre del miércoles hecho el jueves no sale rotulado como jueves.
     */
    fun determine(transactions: List<OpenTransaction>, nowMillis: Long, tz: TimeZone = TimeZone.getDefault()): ReportDate {
        val now = LocalDay.of(nowMillis, tz)
        val parts = DateParts.of(nowMillis, tz)
        val fallback = ReportDate(now, parts.hora.substringBefore(':').toInt(), parts.hora.substringAfter(':').toInt())
        if (transactions.isEmpty()) return fallback

        val first = transactions.firstOrNull { !it.nombre.startsWith(CANCELLATION_PREFIX) } ?: transactions.first()
        val ano = first.ano ?: now.year.toString()
        val sameDay = transactions.all { it.dia == first.dia && it.mes == first.mes && (it.ano ?: ano) == ano }
        if (!sameDay) return fallback

        val y = ano.toIntOrNull(); val m = first.mes.toIntOrNull(); val d = first.dia.toIntOrNull()
        if (y == null || m == null || d == null) return fallback

        var hour = 23; var minute = 59
        val last = transactions.last().hora
        if (last.contains(':')) {
            val p = last.split(':')
            hour = p[0].toIntOrNull() ?: hour
            minute = p.getOrNull(1)?.toIntOrNull() ?: minute
        }
        return ReportDate(LocalDay(y, m, d), hour, minute)
    }
}

sealed interface CloseResult {
    data object Empty : CloseResult
    data class Done(val closing: DailyClosing) : CloseResult
    /** La impresión falló: la caja queda intacta para reintentar. */
    data class PrintFailed(val cause: Throwable) : CloseResult
    /** Se imprimió pero no se pudo guardar el cierre: la caja queda intacta. */
    data class SaveFailed(val cause: Throwable) : CloseResult
}

/** Guarda el cierre del día (lo implementa [ClosingService] sobre Room). */
interface ManualClosingSaver {
    suspend fun saveManualClosing(transactions: List<OpenTransaction>, day: LocalDay, pdfFilename: String? = null): DailyClosing
}

/**
 * "Cerrar Caja e Imprimir": imprime el reporte, guarda el cierre del día en Room
 * (lo fusiona si ya existía) y solo entonces vacía la caja.
 */
class CloseRegister(
    private val register: CashRegister,
    private val closing: ManualClosingSaver,
    private val printer: () -> ReportPrinter,
    private val clock: Clock = SystemClock,
    private val timeZone: TimeZone = TimeZone.getDefault(),
) {
    suspend operator fun invoke(): CloseResult {
        if (!register.hasActive()) return CloseResult.Empty
        val transactions = register.ordered()
        val date = ReportDates.determine(transactions, clock.nowMillis(), timeZone)

        runCatching { printer().printReport(CashReport(transactions, register.total(), date)) }
            .onFailure { return CloseResult.PrintFailed(it) }

        val saved = runCatching { closing.saveManualClosing(transactions, date.day) }
            .getOrElse { return CloseResult.SaveFailed(it) }
        register.clear()
        return CloseResult.Done(saved)
    }
}
