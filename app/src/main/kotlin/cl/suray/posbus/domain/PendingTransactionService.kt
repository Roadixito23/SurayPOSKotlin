package cl.suray.posbus.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Alerta de ventas de días anteriores sin cerrar; se revisa cada 5 minutos. */
class PendingTransactionService(private val register: CashRegister) {
    private var job: Job? = null

    /** Evita repetir la alerta; se reactiva 5 min después de cerrar el diálogo. */
    var hasShownAlert: Boolean = false
        private set

    fun hasPreviousDayTransactions(): Boolean = register.hasPendingOld()

    fun pendingDays(): Int = register.oldestPendingDays()

    /** Marca la alerta como mostrada; llamar a [onAlertDismissed] cuando el usuario la cierre. */
    fun markAlertShown() {
        hasShownAlert = true
    }

    fun onAlertDismissed(scope: CoroutineScope) {
        scope.launch {
            delay(CHECK_INTERVAL_MS)
            hasShownAlert = false
        }
    }

    fun startPeriodicCheck(scope: CoroutineScope, onShowAlert: () -> Unit) {
        job?.cancel()
        job = scope.launch {
            while (true) {
                delay(CHECK_INTERVAL_MS)
                if (hasPreviousDayTransactions() && !hasShownAlert) onShowAlert()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        const val CHECK_INTERVAL_MS = 5 * 60 * 1000L
    }
}
