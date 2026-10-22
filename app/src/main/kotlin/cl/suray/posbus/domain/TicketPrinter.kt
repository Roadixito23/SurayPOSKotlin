package cl.suray.posbus.domain

/** Impresión de tickets; la implementación real (PDF 58 mm) llega en la fase de integraciones. */
interface TicketPrinter {
    /** Debe lanzar excepción si no pudo imprimir: la venta ya está guardada y no se revierte. */
    suspend fun printTicket(nombre: String, valor: Double, comprobante: String, sundayRate: Boolean, reprint: Boolean = false)
}

object UnavailableTicketPrinter : TicketPrinter {
    override suspend fun printTicket(nombre: String, valor: Double, comprobante: String, sundayRate: Boolean, reprint: Boolean) {
        throw UnsupportedOperationException("Impresión aún no implementada")
    }
}
