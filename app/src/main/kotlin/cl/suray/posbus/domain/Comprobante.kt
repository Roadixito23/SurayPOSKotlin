package cl.suray.posbus.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface ComprobanteStore {
    var comprobanteNumber: Int
    var ticketId: Int
}

/** Contador de comprobantes: `"%02d-%06d"`, vuelve a 1 (no a 0) después de 999999. */
class ComprobanteCounter(private val store: ComprobanteStore) {
    private val _number = MutableStateFlow(store.comprobanteNumber)

    /** Último número usado; el próximo a imprimir es `number + 1`. */
    val numberState: StateFlow<Int> = _number

    val number: Int get() = store.comprobanteNumber
    val ticketId: Int get() = store.ticketId

    val formatted: String get() = format(store.ticketId, store.comprobanteNumber)

    fun increment() {
        var next = store.comprobanteNumber + 1
        if (next > MAX_NUMBER) next = 1
        store.comprobanteNumber = next
        _number.value = next
    }

    /** Al anular la última venta el contador retrocede 1, pero nunca por debajo de 1 (igual que Flutter). */
    fun rollbackLast() {
        if (store.comprobanteNumber > 1) {
            store.comprobanteNumber -= 1
            _number.value = store.comprobanteNumber
        }
    }

    fun reset() {
        store.comprobanteNumber = 0
        _number.value = 0
    }

    companion object {
        const val MAX_NUMBER = 999_999

        fun format(ticketId: Int, number: Int): String =
            "${ticketId.toString().padStart(2, '0')}-${number.toString().padStart(6, '0')}"
    }
}
