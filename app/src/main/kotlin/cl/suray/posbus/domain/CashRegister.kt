package cl.suray.posbus.domain

import cl.suray.posbus.data.prefs.OpenTransaction
import java.util.TimeZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

const val CANCELLATION_PREFIX = "Anulación:"
const val CARGO_PREFIX = "Cargo:"
const val OFFER_NAME = "Oferta Ruta"

/** Caja abierta del día (port de `ReporteCaja`). La persistencia es inmediata en cada cambio. */
class CashRegister(
    private val store: OpenRegisterStore,
    private val clock: Clock = SystemClock,
    private val timeZone: TimeZone = TimeZone.getDefault(),
) {
    private val transactions: MutableList<OpenTransaction> = store.openTransactions.toMutableList()
    private var nextId: Int = store.openNextId.let { saved ->
        // Sin contador guardado (1 es el valor por defecto) se recalcula desde los datos cargados.
        if (saved == 1 && transactions.isNotEmpty()) transactions.maxOf { it.id } + 1 else saved
    }

    private val _changes = MutableStateFlow(transactions.toList())

    /** Se emite en cada cambio; la UI se suscribe aquí. */
    val changes: StateFlow<List<OpenTransaction>> = _changes

    val all: List<OpenTransaction> get() = transactions.toList()

    fun receiveData(nombre: String, valor: Double, comprobante: String) =
        add(nombre, valor, comprobante)

    fun receiveCargoData(destinatario: String, precio: Double, comprobante: String) =
        add("$CARGO_PREFIX $destinatario", precio, comprobante)

    fun addOffer(total: Double, comprobante: String) = add(OFFER_NAME, total, comprobante)

    /** Anula la última venta que no sea ya una anulación, con el valor negado. */
    fun cancelLast(): Boolean {
        val last = transactions.lastOrNull { !it.nombre.startsWith(CANCELLATION_PREFIX) } ?: return false
        add("$CANCELLATION_PREFIX ${last.nombre}", -last.valor, last.comprobante)
        return true
    }

    fun ordered(): List<OpenTransaction> = transactions.sortedBy { it.id }

    fun remove(id: Int) {
        transactions.removeAll { it.id == id }
        save()
    }

    fun total(): Double = transactions.sumOf { it.valor }

    fun hasActive(): Boolean = transactions.isNotEmpty()

    fun clear() {
        transactions.clear()
        nextId = 1
        save()
    }

    fun byDate(dia: String, mes: String, ano: String): List<OpenTransaction> =
        transactions.filter { it.dia == dia && it.mes == mes && (it.ano == ano || it.ano == null) }

    /** Hay ventas (no anulaciones) de un día anterior a [today] sin cerrar. */
    fun hasPendingOld(today: LocalDay = today()): Boolean =
        transactions.any { t ->
            if (t.nombre.startsWith(CANCELLATION_PREFIX)) return@any false
            dayOf(t, today) < today
        }

    /** Antigüedad en días de la venta pendiente más vieja (0 si no hay). */
    fun oldestPendingDays(today: LocalDay = today()): Int =
        transactions
            .filterNot { it.nombre.startsWith(CANCELLATION_PREFIX) }
            .maxOfOrNull { dayOf(it, today).daysUntil(today).toInt() }
            ?.coerceAtLeast(0) ?: 0

    private fun today() = LocalDay.of(clock.nowMillis(), timeZone)

    /** Fecha de la venta; los campos ilegibles se toman de [today], como en Flutter. */
    private fun dayOf(t: OpenTransaction, today: LocalDay) = LocalDay(
        year = t.ano?.toIntOrNull() ?: today.year,
        month = t.mes.toIntOrNull() ?: today.month,
        day = t.dia.toIntOrNull() ?: today.day,
    )

    private fun add(nombre: String, valor: Double, comprobante: String) {
        val now = clock.nowMillis()
        val parts = DateParts.of(now, timeZone)
        transactions += OpenTransaction(
            id = nextId++,
            nombre = nombre,
            valor = valor,
            comprobante = comprobante,
            dia = parts.dia,
            mes = parts.mes,
            ano = parts.ano,
            hora = parts.hora,
            timestamp = now,
        )
        save()
    }

    private fun save() {
        store.openTransactions = transactions.toList()
        store.openNextId = nextId
        _changes.value = transactions.toList()
    }
}
