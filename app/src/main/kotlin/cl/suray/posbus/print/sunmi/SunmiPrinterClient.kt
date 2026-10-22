package cl.suray.posbus.print.sunmi

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.os.SystemClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import woyou.aidlservice.jiuiv5.ICallback
import woyou.aidlservice.jiuiv5.IWoyouService

class SunmiUnavailableException(message: String) : Exception(message)

/** Resultado de un comando: [events] son todos los avisos del callback, en orden de llegada. */
data class CallResult(val ok: Boolean, val millis: Long, val events: List<String>) {
    val summary: String get() = (if (ok) "OK" else "FALLO") + " ${millis}ms" + if (events.isEmpty()) "" else " [" + events.joinToString("; ") + "]"
}

/**
 * Conexión con el servicio de impresión de Sunmi (`woyou.aidlservice.jiuiv5`). No requiere permisos.
 * El servicio puede morir o reiniciarse: [connect] vuelve a enlazar si hace falta.
 */
class SunmiPrinterClient(private val context: Context) {
    @Volatile private var service: IWoyouService? = null
    private var connection: ServiceConnection? = null

    val isConnected: Boolean get() = service?.asBinder()?.isBinderAlive == true

    /** Enlaza con el servicio (o reutiliza la conexión viva). Lanza [SunmiUnavailableException] si no se logra. */
    suspend fun connect(timeoutMs: Long = 4_000): IWoyouService {
        service?.takeIf { it.asBinder().isBinderAlive }?.let { return it }
        disconnect()
        val ready = CompletableDeferred<IWoyouService>()
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                IWoyouService.Stub.asInterface(binder).also { service = it; ready.complete(it) }
            }
            override fun onServiceDisconnected(name: ComponentName) { service = null }
        }
        val intent = Intent(SERVICE_ACTION).setPackage(SERVICE_PACKAGE)
        val bound = try { context.bindService(intent, conn, Context.BIND_AUTO_CREATE) } catch (e: SecurityException) { false }
        if (!bound) throw SunmiUnavailableException("No se encontró el servicio de impresión de Sunmi")
        connection = conn
        return try {
            withTimeout(timeoutMs) { ready.await() }
        } catch (e: TimeoutCancellationException) {
            disconnect()
            throw SunmiUnavailableException("El servicio de impresión no respondió")
        }
    }

    fun disconnect() {
        connection?.let { runCatching { context.unbindService(it) } }
        connection = null
        service = null
    }

    /**
     * Ejecuta un comando con callback y espera su resultado. Termina con el primer aviso definitivo
     * (`onRunResult`, `onReturnString` en las consultas, `onRaiseException` o `onPrintResult`) y recoge un instante más los avisos tardíos.
     */
    suspend fun call(timeoutMs: Long = 8_000, block: IWoyouService.(ICallback) -> Unit): CallResult {
        val svc = connect()
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        val first = CompletableDeferred<Boolean>()
        val cb = object : ICallback.Stub() {
            override fun onRunResult(isSuccess: Boolean) { events += "run=$isSuccess"; first.complete(isSuccess) }
            override fun onReturnString(result: String?) { events += "string=$result"; first.complete(true) }
            override fun onRaiseException(code: Int, msg: String?) { events += "excepción($code,$msg)"; first.complete(false) }
            override fun onPrintResult(code: Int, msg: String?) { events += "print($code,$msg)"; first.complete(code == 0 || code == 1) }
        }
        val t0 = SystemClock.elapsedRealtime()
        val ok = try {
            svc.block(cb)
            try { withTimeout(timeoutMs) { first.await() } } catch (e: TimeoutCancellationException) { events += "sin respuesta"; false }
        } catch (e: RemoteException) {
            events += "RemoteException: ${e.message}"; service = null; false
        }
        delay(250) // avisos tardíos (p. ej. onPrintResult tras onRunResult)
        return CallResult(ok, SystemClock.elapsedRealtime() - t0, events.toList())
    }

    companion object {
        const val SERVICE_PACKAGE = "woyou.aidlservice.jiuiv5"
        const val SERVICE_ACTION = "woyou.aidlservice.jiuiv5.IWoyouService"

        /** Avisos de estado que emite el servicio (se ve en su APK; nótese el `jiuv5` sin «i» y la errata `ACITON`). */
        val STATE_ACTIONS = listOf(
            "NORMAL_ACTION", "OUT_OF_PAPER_ACTION", "LESS_OF_PAPER_ACTION", "COVER_OPEN_ACTION", "COVER_ERROR_ACTION",
            "OVER_HEATING_ACITON", "NORMAL_HEATING_ACITON", "MOTOR_HEATING_ACITON", "PAPER_ERROR_ACITON", "ERROR_ACTION",
            "INIT_ACTION", "FIRMWARE_UPDATING_ACITON", "FIRMWARE_FINISH_ACITON", "FIRMWARE_FAILURE_ACITON",
            "KNIFE_ERROR_ACTION_1", "KNIFE_ERROR_ACTION_2", "PRINTER_NON_EXISTENT_ACITON",
        ).map { "woyou.aidlservice.jiuv5.$it" }
    }
}
