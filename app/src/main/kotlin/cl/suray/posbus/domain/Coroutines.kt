package cl.suray.posbus.domain

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Como `runCatching`, pero respeta la cancelación estructurada: si la corrutina propia fue
 * cancelada, relanza la excepción en vez de tragarla. Un `CancellationException` interno
 * (p. ej. un `TimeoutCancellationException`) con la corrutina aún activa se devuelve como `failure`.
 */
suspend inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
        Result.failure(e)
    } catch (e: Throwable) {
        Result.failure(e)
    }
