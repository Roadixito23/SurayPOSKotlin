package cl.suray.posbus.domain

import cl.suray.posbus.data.prefs.CargoData
import cl.suray.posbus.data.prefs.OfferEntry

/** Impresión de tickets en papel de 58 mm. Todas las operaciones lanzan excepción si no se imprimió. */
interface TicketPrinter {
    /** La venta ya está guardada y no se revierte si falla. */
    suspend fun printTicket(nombre: String, valor: Double, comprobante: String, sundayRate: Boolean, reprint: Boolean = false)

    suspend fun printOffer(entries: List<OfferEntry>, comprobante: String, reprint: Boolean = false)

    /** Imprime las [copies] en orden, una tras otra (cada una es un trabajo de impresión). */
    suspend fun printCargo(cargo: CargoData, comprobante: String, copies: List<CargoCopy>, reprint: Boolean = false)
}

object UnavailableTicketPrinter : TicketPrinter {
    private fun fail(): Nothing = throw UnsupportedOperationException("Impresión no disponible")
    override suspend fun printTicket(nombre: String, valor: Double, comprobante: String, sundayRate: Boolean, reprint: Boolean) = fail()
    override suspend fun printOffer(entries: List<OfferEntry>, comprobante: String, reprint: Boolean) = fail()
    override suspend fun printCargo(cargo: CargoData, comprobante: String, copies: List<CargoCopy>, reprint: Boolean) = fail()
}

/** Reimpresión de un PDF ya generado (reportes guardados). Lanza excepción si no se imprimió. */
fun interface PdfFilePrinter {
    suspend fun printPdf(file: java.io.File, jobName: String)
}
