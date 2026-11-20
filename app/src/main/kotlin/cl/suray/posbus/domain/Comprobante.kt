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

    val number: Int get() = _number.value
    val ticketId: Int get() = store.ticketId

    val formatted: String get() = format(store.ticketId, _number.value)

    /** Vuelve a leer el contador del almacén (p. ej. tras descartar un lote de escrituras que falló). */
    fun reload() {
        _number.value = store.comprobanteNumber
    }

    // El número en memoria es la referencia: dentro de un lote de escrituras el almacén aún no confirma.
    fun increment() {
        var next = _number.value + 1
        if (next > MAX_NUMBER) next = 1
        store.comprobanteNumber = next
        _number.value = next
    }

    /** Al anular la última venta el contador retrocede 1, pero nunca por debajo de 1 (igual que Flutter). */
    fun rollbackLast() {
        if (_number.value > 1) {
            store.comprobanteNumber = _number.value - 1
            _number.value = _number.value - 1
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
