package cl.suray.posbus.domain

/**
 * Ventas de días anteriores sin cerrar. El aviso lo decide Inicio: un diálogo al aparecer la caja pendiente
 * y un banner mientras dure (antes había además una alerta repetida cada 5 minutos).
 */
class PendingTransactionService(private val register: CashRegister) {
    fun hasPreviousDayTransactions(): Boolean = register.hasPendingOld()

    fun pendingDays(): Int = register.oldestPendingDays()
}
