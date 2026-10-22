package cl.suray.posbus.domain

class PrintFailure(message: String, val code: Int? = null) : Exception(message)

data class CommitResult(val ok: Boolean, val code: Int?, val message: String?)

/** Impresora de recibos de texto. La implementación Sunmi está en `print/sunmi`; aquí solo el contrato. */
interface ReceiptDevice {
    /** Estado de la impresora (`PrinterState`). Puede lanzar si no hay conexión. */
    suspend fun state(): Int
    /** Inicializa y abre un trabajo (modo buffer limpio): nada sale al papel hasta [commit]. */
    suspend fun begin()
    fun text(line: ReceiptLine.Text)
    fun columns(line: ReceiptLine.Columns)
    fun feed(lines: Int)
    /** Cierra el trabajo y espera la confirmación del servicio. */
    suspend fun commit(): CommitResult
    /** Descarta lo encolado sin imprimir (se usa si algo falla a mitad). */
    fun discard()
}

/**
 * Imprime una lista de líneas con las garantías que necesita el cierre de caja:
 * comprueba el estado ANTES de empezar, envía todo en un solo trabajo y solo da el éxito
 * cuando el servicio confirma el trabajo completo. Cualquier fallo lanza [PrintFailure].
 */
class ReceiptEngine(private val device: ReceiptDevice) {

    suspend fun print(lines: List<ReceiptLine>) {
        val state = try { device.state() } catch (e: PrintFailure) { throw e } catch (e: Exception) {
            throw PrintFailure("No se pudo consultar la impresora: ${e.message}")
        }
        if (!PrinterState.isReady(state)) throw PrintFailure("La impresora está ${PrinterState.describe(state)}", state)

        try {
            device.begin()
            for (line in lines) when (line) {
                is ReceiptLine.Text -> device.text(line)
                is ReceiptLine.Columns -> device.columns(ReceiptLayouts.fit(line))
                is ReceiptLine.Feed -> device.feed(line.lines)
            }
        } catch (e: Exception) {
            device.discard()
            throw if (e is PrintFailure) e else PrintFailure("Error al enviar a la impresora: ${e.message}")
        }

        val result = try { device.commit() } catch (e: Exception) {
            device.discard()
            throw PrintFailure("Error al confirmar la impresión: ${e.message}")
        }
        if (!result.ok) {
            device.discard()
            throw PrintFailure(result.message ?: "La impresora no confirmó el trabajo", result.code)
        }
    }
}
