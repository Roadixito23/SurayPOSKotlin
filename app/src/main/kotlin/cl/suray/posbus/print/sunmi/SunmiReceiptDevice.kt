package cl.suray.posbus.print.sunmi

import cl.suray.posbus.domain.CommitResult
import cl.suray.posbus.domain.PrintFailure
import cl.suray.posbus.domain.ReceiptDevice
import cl.suray.posbus.domain.ReceiptLine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import woyou.aidlservice.jiuiv5.ICallback
import woyou.aidlservice.jiuiv5.IWoyouService

/**
 * Implementa [ReceiptDevice] sobre el servicio de impresión Sunmi usando el modo buffer:
 * `printerInit` → `enterPrinterBuffer(true)` → comandos (sin esperar callback: se encolan) →
 * `exitPrinterBufferWithCallback(true)`, cuyo `onPrintResult(0, …)` confirma el trabajo completo.
 * Hallazgos y códigos en `docs/sunmi-aidl-4.15.2.md`.
 */
class SunmiReceiptDevice(private val client: SunmiPrinterClient) : ReceiptDevice {
    private var svc: IWoyouService? = null

    /** Dentro del buffer los comandos no responden; el servicio exige un callback, así que se le pasa uno que no hace nada. */
    private val noop = object : ICallback.Stub() {
        override fun onRunResult(isSuccess: Boolean) {}
        override fun onReturnString(result: String?) {}
        override fun onRaiseException(code: Int, msg: String?) {}
        override fun onPrintResult(code: Int, msg: String?) {}
    }

    // Estilo actual dentro del trabajo, para no repetir llamadas al servicio.
    private var align = -1
    private var size = -1
    private var bold = false
    private var inverted = false

    private fun service() = svc ?: throw PrintFailure("Sin conexión con la impresora")

    private fun raw(vararg b: Int) = service().sendRAWData(ByteArray(b.size) { b[it].toByte() }, noop)

    override suspend fun state(): Int {
        val s = try { client.connect() } catch (e: SunmiUnavailableException) { throw PrintFailure(e.message ?: "Impresora no disponible") }
        svc = s
        return s.updatePrinterState()
    }

    override suspend fun begin() {
        val s = svc ?: client.connect().also { svc = it }
        val init = client.call { printerInit(it) }
        if (!init.ok) throw PrintFailure("No se pudo inicializar la impresora (${init.summary})")
        s.enterPrinterBuffer(true)
        align = -1; size = -1; bold = false; inverted = false
    }

    private fun style(a: Int, sz: Int, b: Boolean, inv: Boolean) {
        val s = service()
        if (a != align) { s.setAlignment(a, noop); align = a }
        if (sz != size) { s.setFontSize(sz.toFloat(), noop); size = sz }
        if (b != bold) { raw(0x1B, 0x45, if (b) 1 else 0); bold = b }
        if (inv != inverted) { raw(0x1D, 0x42, if (inv) 1 else 0); inverted = inv }
    }

    override fun text(line: ReceiptLine.Text) {
        style(line.align.code, line.size, line.bold, line.inverted)
        service().printText(line.text + "\n", noop)
    }

    override fun columns(line: ReceiptLine.Columns) {
        style(0, 24, line.bold, false)
        service().printColumnsText(line.cells.toTypedArray(), line.widths.toIntArray(), IntArray(line.aligns.size) { line.aligns[it].code }, noop)
    }

    override fun feed(lines: Int) { service().lineWrap(lines, noop) }

    override suspend fun commit(): CommitResult {
        val s = service()
        style(0, 24, false, false)
        val done = CompletableDeferred<CommitResult>()
        val cb = object : ICallback.Stub() {
            override fun onRunResult(isSuccess: Boolean) { if (!isSuccess) done.complete(CommitResult(false, null, "La impresora rechazó el trabajo")) }
            override fun onReturnString(result: String?) {}
            override fun onRaiseException(code: Int, msg: String?) { done.complete(CommitResult(false, code, msg ?: "Error de la impresora ($code)")) }
            override fun onPrintResult(code: Int, msg: String?) { done.complete(CommitResult(code == 0, code, msg)) }
        }
        s.exitPrinterBufferWithCallback(true, cb)
        return try {
            withTimeout(COMMIT_TIMEOUT_MS) { done.await() }
        } catch (e: TimeoutCancellationException) {
            CommitResult(false, null, "La impresora no confirmó el trabajo a tiempo")
        }
    }

    override fun discard() {
        runCatching { svc?.exitPrinterBuffer(false) }
    }

    private companion object { const val COMMIT_TIMEOUT_MS = 20_000L }
}
