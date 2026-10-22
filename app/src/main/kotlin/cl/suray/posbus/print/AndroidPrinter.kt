package cl.suray.posbus.print

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import cl.suray.posbus.R
import android.graphics.Bitmap
import cl.suray.posbus.data.prefs.AppPrefs
import cl.suray.posbus.data.prefs.CargoData
import cl.suray.posbus.data.prefs.OfferEntry
import cl.suray.posbus.domain.CargoCopy
import cl.suray.posbus.domain.CargoTicketLayout
import cl.suray.posbus.domain.CashReport
import cl.suray.posbus.domain.Clock
import cl.suray.posbus.domain.ReportLayouts
import cl.suray.posbus.domain.PdfFilePrinter
import cl.suray.posbus.domain.ReportPrinter
import cl.suray.posbus.domain.TicketLayouts
import cl.suray.posbus.domain.TicketPrinter
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class PrintCancelledException : Exception("Impresión cancelada")

/**
 * Imprime PDFs de 58 mm con el servicio de impresión de Android (el mismo mecanismo que usa
 * `Printing.layoutPdf` en Flutter). Una impresión cancelada lanza excepción, así que la venta
 * queda guardada pero el cierre de caja no vacía la caja.
 */
class AndroidPrinter(
    private val context: Context,
    private val activity: () -> Activity?,
    private val prefs: AppPrefs,
    private val clock: Clock,
    private val pdfDir: File = File(context.filesDir, "pdfs"),
) : TicketPrinter, ReportPrinter, PdfFilePrinter {

    override suspend fun printTicket(nombre: String, valor: Double, comprobante: String, sundayRate: Boolean, reprint: Boolean) {
        val t = TicketLayouts.regular(nombre, valor, comprobante, sundayRate, clock.nowMillis(), reprint)
        val bytes = withContext(Dispatchers.Default) {
            val res = context.resources
            val logo = BitmapFactory.decodeResource(res, R.drawable.logobkwt)
            val end = BitmapFactory.decodeResource(res, R.drawable.endticket)
            renderPdf { pen ->
                pen.logoWithBox(logo, listOf(Triple("COMPROBANTE DE", 8f, true), Triple("PAGO EN BUS", 8f, true), Triple(t.numberLine, 10f, true)))
                pen.space(5f)
                if (t.isReprint) pen.boxedCentered("REIMPRESIÓN", 10f, true, 2f, 5f, fill = Color.LTGRAY)
                pen.centered(t.tariffLine, 12f, bold = true)
                pen.centered(t.tipo, 12f)
                pen.space(5f)
                pen.boxedCentered(t.valueLine, 15f, true, 4f, 8f)
                pen.space(5f)
                pen.centered("Válido hora y fecha señaladas", 10f, bold = true)
                pen.space(3f)
                pen.spaced(t.time, t.date, 10f, bold = true)
                t.reprintedAt?.let { pen.space(5f); pen.centered("Reimpreso: $it", 8f, italic = true) }
                pen.bitmap(end, pen.width)
            }
        }
        print("Ticket ${t.numberLine}", bytes)
    }

    override suspend fun printOffer(entries: List<OfferEntry>, comprobante: String, reprint: Boolean) {
        val t = TicketLayouts.offer(entries, comprobante, clock.nowMillis(), reprint)
        val bytes = withContext(Dispatchers.Default) {
            val logo = BitmapFactory.decodeResource(context.resources, R.drawable.logobkwt)
            val end = BitmapFactory.decodeResource(context.resources, R.drawable.endticket)
            renderPdf { pen ->
                pen.logoWithBox(logo, listOf(Triple("COMPROBANTE DE", 8f, true), Triple("PAGO EN BUS", 8f, true), Triple(t.numberLine, 10f, true)))
                pen.space(5f)
                if (t.isReprint) pen.boxedCentered("REIMPRESIÓN", 10f, true, 2f, 5f, fill = Color.LTGRAY)
                pen.centered("Oferta en Ruta", 12f)
                pen.space(5f)
                pen.centeredTable(listOf("Cant. Pax.", "Precio Unitario"), t.rows.map { listOf(it.first, it.second) }, listOf(1f, 2f), 0.9f)
                pen.space(5f)
                pen.boxedCentered(t.totalLine, 15f, true, 4f, 8f)
                pen.space(5f)
                pen.centered("Válido hora y fecha señaladas", 10f, bold = true)
                pen.space(3f)
                pen.spaced(t.time, t.date, 10f, bold = true)
                t.reprintedAt?.let { pen.space(5f); pen.centered("Reimpreso: $it", 8f, italic = true) }
                pen.bitmap(end, pen.width)
            }
        }
        print("Oferta ${t.numberLine}", bytes)
    }

    override suspend fun printCargo(cargo: CargoData, comprobante: String, copies: List<CargoCopy>, reprint: Boolean) {
        for (copy in copies) {
            val t = TicketLayouts.cargo(cargo, comprobante, copy, clock.nowMillis(), reprint)
            val bytes = withContext(Dispatchers.Default) {
                val res = context.resources
                val logo = BitmapFactory.decodeResource(res, R.drawable.logobkwt)
                val end = BitmapFactory.decodeResource(res, R.drawable.endticket)
                val scissors = BitmapFactory.decodeResource(res, R.drawable.tijera)
                renderPdf { pen -> drawCargo(pen, t, logo, end, scissors) }
            }
            print("Cargo ${t.caption} ${t.numberLine}", bytes)
        }
    }

    private fun drawCargo(pen: PdfPen, t: CargoTicketLayout, logo: Bitmap, end: Bitmap, scissors: Bitmap) {
        val isCargoCopy = t.copy == CargoCopy.Cargo
        if (isCargoCopy) { pen.tapeRectangle(); pen.space(8f) }
        pen.logoWithBox(logo, listOf(Triple("COMPROBANTE DE", 8f, true), Triple("PAGO EN BUS", 8f, true), Triple(t.numberLine, 10f, true)))
        pen.space(4f)
        pen.centered(t.caption, 8f, bold = true)
        pen.space(8f)
        pen.box(listOf(PdfPen.BoxItem.Inline("Destino:", t.destino)))
        pen.space(6f)
        pen.box(listOf(PdfPen.BoxItem.Line("Artículo:", 10f), PdfPen.BoxItem.Line(t.articulo, 12f, bold = true)))
        pen.space(6f)
        pen.box(listOf(PdfPen.BoxItem.Inline("Precio:", t.precioLine)))
        pen.space(6f)
        pen.box(listOf(PdfPen.BoxItem.Line("Destinatario:", 10f), PdfPen.BoxItem.Line(t.destinatario, 12f, bold = true)))
        t.telefono?.let { pen.space(6f); pen.box(listOf(PdfPen.BoxItem.Inline("Teléfono:", it))) }
        pen.space(6f)
        pen.spaced("Fecha: ${t.fecha}", "Hora:  ${t.hora}", 8f)
        pen.space(if (isCargoCopy) 12f else 6f)
        pen.bitmap(end, pen.width * 0.8f)
        if (!isCargoCopy) return
        pen.space(8f)
        pen.tapeRectangle()
        pen.space(8f)
        pen.cutLine(scissors)
        pen.space(12f)
        pen.centered("CONTROL INTERNO", 10f, bold = true)
        pen.space(4f)
        pen.box(listOf(
            PdfPen.BoxItem.Gap(12f), PdfPen.BoxItem.Line("__________________________", 10f), PdfPen.BoxItem.Line("Nombre", 10f),
            PdfPen.BoxItem.Gap(12f), PdfPen.BoxItem.Line("__________________________", 10f), PdfPen.BoxItem.Line("R.U.T.", 10f),
            PdfPen.BoxItem.Gap(16f), PdfPen.BoxItem.Line("___________________________", 10f), PdfPen.BoxItem.Line("                 Firma", 10f),
            PdfPen.BoxItem.Gap(8f), PdfPen.BoxItem.Line("Comprobante N° ${t.comprobante}", 10f, bold = true),
            PdfPen.BoxItem.Gap(4f), PdfPen.BoxItem.Line("Artículo: ${t.articulo}", 10f),
            PdfPen.BoxItem.Gap(4f), PdfPen.BoxItem.Line("Valor: ${t.precioLine}", 10f),
            PdfPen.BoxItem.Gap(4f), PdfPen.BoxItem.Pair("Fecha: ${t.fecha}", "Hora: ${t.hora}", 8f),
        ))
        pen.space(8f)
        pen.box(listOf(
            PdfPen.BoxItem.Line("Entregado el día: ___/___/______", 10f),
            PdfPen.BoxItem.Gap(8f),
            PdfPen.BoxItem.Line("A las: ____ : ____ Hrs.", 10f),
        ))
        pen.space(prefsMargin())
        pen.rule(2f)
    }

    private fun prefsMargin() = prefs.pdfEndMargin

    override suspend fun printPdf(file: File, jobName: String) {
        val (bytes, height) = withContext(Dispatchers.IO) {
            val data = file.readBytes()
            val h = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                android.graphics.pdf.PdfRenderer(fd).use { r -> r.openPage(0).use { it.height } }
            }
            data to h
        }
        print(jobName, RenderedPdf(bytes, height))
    }

    override suspend fun printReport(report: CashReport) {
        val abbreviations = AppPrefs.DEFAULT_ABBREVIATIONS.keys.associateWith(prefs::abbreviation)
        val layout = ReportLayouts.build(report, prefs.ticketId, abbreviations)
        val bytes = withContext(Dispatchers.Default) {
            renderPdf { pen ->
                pen.centered("Transportes Suray Limitada", 14f, bold = true)
                pen.space(3f)
                pen.centered("77.799.670-3", 11f)
                pen.space(2f)
                pen.centered("Eusebio Ibar 630", 11f)
                pen.space(5f)
                pen.fillBar("CIERRE DE CAJA", 12f, Color.DKGRAY, Color.WHITE, 6f)
                pen.space(5f)
                pen.centered(layout.dayName, 14f, bold = true)
                pen.space(5f)
                pen.spaced("ID: ${layout.terminalId}", "Fecha: ${layout.dateTime}", 10f)
                pen.space(5f)
                pen.centered(layout.totalLine, 16f, bold = true)
                pen.space(5f)
                for (s in layout.sections) {
                    pen.centered(s.title, 14f, bold = true)
                    pen.space(5f)
                    if (s.rows.isEmpty()) {
                        pen.centered("No hay transacciones", 12f)
                    } else {
                        pen.table(
                            header = listOf("N°C", "Día", "Hora", "Tipo", "Valor"),
                            rows = s.rows.map { listOf(it.comprobante, it.dia, it.hora, it.tipo, it.valor) },
                            flex = listOf(0.8f, 0.7f, 1.05f, 1.3f, 1.6f),
                            rightAligned = setOf(4),
                        )
                        pen.space(5f)
                        pen.centered(s.totalLine, 11f, bold = true)
                    }
                    pen.space(10f)
                }
                if (!layout.hasCancellations) pen.centered("Sin anulaciones el día de Hoy", 12f, italic = true)
                pen.space(prefs.pdfEndMargin)
                pen.rule(2f)
            }
        }
        print("Cierre de caja ${layout.dateTime}", bytes)
        // Solo si se imprimió: un intento cancelado no deja copia.
        runCatching { saveCopy(layout.dayName, report, bytes.bytes) }
    }

    /** Copia local del reporte (`reporte_DIA_d_m_yy(n).pdf`), como hace Flutter para la recuperación de reportes. */
    private fun saveCopy(dayName: String, report: CashReport, bytes: ByteArray) {
        pdfDir.mkdirs()
        val d = report.date.day
        val base = "reporte_${dayName}_${d.day}_${d.month}_${d.year % 100}"
        val used = pdfDir.list().orEmpty().mapNotNull { Regex("^" + Regex.escape(base) + "(?:\\((\\d+)\\))?\\.pdf$").find(it)?.groupValues?.get(1)?.toIntOrNull() ?: if (it == "$base.pdf") 1 else null }
        File(pdfDir, "$base(${(used.maxOrNull() ?: 0) + 1}).pdf").writeBytes(bytes)
    }

    /**
     * Espera a que el usuario confirme en el diálogo. El servicio de impresión escribe el PDF
     * para la vista previa antes de la confirmación, así que el éxito se decide por el estado
     * del trabajo (en cola / en curso / completado), no por haber escrito el documento.
     */
    private suspend fun print(jobName: String, rendered: RenderedPdf) {
        val pdf = rendered.bytes
        val host = activity() ?: throw IllegalStateException("No hay pantalla activa para imprimir")
        val job = withContext(Dispatchers.Main) {
            val adapter = object : PrintDocumentAdapter() {
                override fun onLayout(old: PrintAttributes?, new: PrintAttributes, signal: CancellationSignal, cb: LayoutResultCallback, extras: Bundle?) {
                    if (signal.isCanceled) return cb.onLayoutCancelled()
                    cb.onLayoutFinished(
                        PrintDocumentInfo.Builder("$jobName.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1).build(),
                        old != new,
                    )
                }

                override fun onWrite(pages: Array<PageRange>, dest: ParcelFileDescriptor, signal: CancellationSignal, cb: WriteResultCallback) {
                    try {
                        FileOutputStream(dest.fileDescriptor).use { it.write(pdf) }
                        cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        cb.onWriteFailed(e.message)
                    }
                }
            }
            val attrs = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize("suray_58mm", "58 mm", 2283, rendered.heightPt * 1000 / 72))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()
            (host.getSystemService(Context.PRINT_SERVICE) as PrintManager).print(jobName, adapter, attrs)
        }
        val deadline = clock.nowMillis() + CONFIRM_TIMEOUT_MS
        while (true) {
            when {
                job.isQueued || job.isStarted || job.isCompleted -> return
                job.isCancelled -> throw PrintCancelledException()
                job.isFailed -> throw IllegalStateException("Falló el trabajo de impresión")
            }
            if (clock.nowMillis() > deadline) {
                withContext(Dispatchers.Main) { job.cancel() }
                throw PrintCancelledException()
            }
            delay(POLL_MS)
        }
    }

    private companion object {
        const val POLL_MS = 150L
        const val CONFIRM_TIMEOUT_MS = 5 * 60_000L
    }
}
