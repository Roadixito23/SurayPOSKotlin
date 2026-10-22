package cl.suray.posbus.print.sunmi

import cl.suray.posbus.data.prefs.AppPrefs
import cl.suray.posbus.domain.CashReport
import cl.suray.posbus.domain.ReceiptEngine
import cl.suray.posbus.domain.ReceiptLayouts
import cl.suray.posbus.domain.ReportLayouts
import cl.suray.posbus.domain.ReportPrinter

/**
 * Cierre de caja por impresión directa Sunmi (texto en modo buffer). Lanza [cl.suray.posbus.domain.PrintFailure]
 * si la impresora no está lista o no confirma el trabajo, igual que el contrato de [ReportPrinter].
 * No guarda el PDF de recuperación: eso lo decide quien confirma el cierre (Fase 3).
 */
class SunmiReportPrinter(client: SunmiPrinterClient, private val prefs: AppPrefs) : ReportPrinter {
    private val engine = ReceiptEngine(SunmiReceiptDevice(client))

    override suspend fun printReport(report: CashReport) {
        val abbreviations = AppPrefs.DEFAULT_ABBREVIATIONS.keys.associateWith(prefs::abbreviation)
        val layout = ReportLayouts.build(report, prefs.ticketId, abbreviations)
        engine.print(ReceiptLayouts.report(layout, prefs.pdfEndMargin))
    }
}
