package cl.suray.posbus.domain

import androidx.room.withTransaction
import cl.suray.posbus.data.db.AppDatabase
import cl.suray.posbus.data.db.DailyClosing
import cl.suray.posbus.data.db.TransactionEntity
import cl.suray.posbus.data.prefs.OpenTransaction
import java.util.TimeZone

/** Cierre automático al cambiar de día y fusión de cierres del mismo día (`AutoClosingService` + `MergeService`). */
class ClosingService(
    private val db: AppDatabase,
    private val lastDate: LastDateStore,
    private val register: CashRegister,
    private val clock: Clock = SystemClock,
    private val timeZone: TimeZone = TimeZone.getDefault(),
) : ManualClosingSaver {
    private fun todayIso() = LocalDay.of(clock.nowMillis(), timeZone).iso()

    /** @return true si se cerró la caja del día anterior. */
    suspend fun checkAndAutoClose(): Boolean {
        val today = todayIso()
        val last = lastDate.lastTransactionDate
        if (last == null || last == today) return false

        if (!register.hasActive()) {
            lastDate.lastTransactionDate = today
            return false
        }
        val closed = runCatching { performAutoClose(last) }.getOrDefault(false)
        if (closed) lastDate.lastTransactionDate = today
        return closed
    }

    fun updateLastTransactionDate() {
        lastDate.lastTransactionDate = todayIso()
    }

    fun shouldShowDayChangeAlert(): Boolean {
        val last = lastDate.lastTransactionDate
        return last != null && last != todayIso() && register.hasActive()
    }

    private suspend fun performAutoClose(closingDate: String): Boolean {
        val transactions = register.ordered()
        if (transactions.isEmpty()) return false
        val day = LocalDay.parse(closingDate) ?: return false

        val existing = db.dailyClosingDao().getByDate(closingDate)
        if (existing != null) merge(existing, transactions)
        else createNew(transactions, day, ClosingTotals.forAutoClose(transactions))

        register.clear()
        return true
    }

    private suspend fun createNew(
        transactions: List<OpenTransaction>,
        day: LocalDay,
        totals: Totals,
        pdfFilename: String? = null,
    ): DailyClosing {
        val now = clock.nowMillis()
        return db.withTransaction {
            val closing = DailyClosing(
                    date = day.iso(),
                    year = day.year,
                    month = day.month,
                    day = day.day,
                    dayOfWeek = DAYS_ES_UPPER[day.dayOfWeek - 1],
                    totalPasajes = totals.pasajes,
                    totalCorrespondencias = totals.correspondencias,
                    totalAnulaciones = totals.anulaciones,
                    grandTotal = totals.grandTotal,
                    transactionCount = transactions.size,
                    status = "closed",
                    createdAt = now,
                    closedAt = now,
                    pdfFilename = pdfFilename,
            )
            val id = db.dailyClosingDao().insert(closing)
            db.transactionDao().insertAll(transactions.map { it.toEntity(id, day.year) })
            closing.copy(id = id)
        }
    }

    /**
     * Cierre manual: crea el cierre de [day] o lo fusiona con el que ya existe.
     * Los totales de un cierre nuevo usan la clasificación sensible a mayúsculas (la misma
     * que recalcula la fusión), para que crear y fusionar den siempre el mismo resultado.
     */
    override suspend fun saveManualClosing(transactions: List<OpenTransaction>, day: LocalDay, pdfFilename: String?): DailyClosing {
        val existing = db.dailyClosingDao().getByDate(day.iso())
        return if (existing != null) merge(existing, transactions)
        else createNew(transactions, day, ClosingTotals.fromOpen(transactions), pdfFilename)
    }

    /**
     * Agrega [newTransactions] a un cierre existente y recalcula sus totales.
     *
     * Es idempotente: guardar en Room y vaciar la caja (SharedPreferences) no es atómico, así que
     * si el proceso muere entre ambos pasos el siguiente cierre del mismo día vuelve a traer las
     * mismas ventas. Por eso, dentro de la transacción, se descartan las que ya están en el cierre
     * (misma huella, ver [newTransactionsOnly]). Si no queda nada nuevo, no se escribe nada y se
     * devuelve [existing] tal cual (totales, conteo y `closedAt` sin cambios).
     */
    suspend fun merge(existing: DailyClosing, newTransactions: List<OpenTransaction>): DailyClosing =
        db.withTransaction {
            val old = db.transactionDao().getByClosingId(existing.id)
            val added = newTransactionsOnly(old, newTransactions.map { it.toEntity(existing.id, existing.year) })
            if (added.isEmpty()) return@withTransaction existing
            db.transactionDao().insertAll(added)

            val all = old + added
            val totals = ClosingTotals.forMerge(all)
            val updated = existing.copy(
                totalPasajes = totals.pasajes,
                totalCorrespondencias = totals.correspondencias,
                totalAnulaciones = totals.anulaciones,
                grandTotal = totals.grandTotal,
                transactionCount = all.size,
                closedAt = clock.nowMillis(),
            )
            db.dailyClosingDao().update(updated)
            updated
        }
}

/**
 * Huella estable de una venta guardada: comprobante + nombre + valor + timestamp (milisegundos).
 *
 * No se usan `id` ni `transactionId`: el primero lo asigna Room y el segundo se reinicia al vaciar
 * la caja. `dia`/`mes`/`ano`/`hora` se derivan del timestamp, y `category`/`isCancellation` del
 * nombre, así que no aportan. El timestamp en milisegundos distingue ventas legítimas aunque
 * se repita el comprobante (al anular, el contador retrocede y se reutiliza el número).
 */
internal data class SaleFingerprint(val comprobante: String, val nombre: String, val valor: Double, val timestamp: Long)

internal fun TransactionEntity.fingerprint() = SaleFingerprint(comprobante, nombre, valor, timestamp)

/**
 * Filtra de [incoming] las ventas cuya huella ya está en [existing] (las de un cierre anterior
 * del mismo día). No deduplica dentro de [incoming]: la caja no repite ventas por sí misma.
 */
internal fun newTransactionsOnly(existing: List<TransactionEntity>, incoming: List<TransactionEntity>): List<TransactionEntity> {
    val seen = existing.mapTo(HashSet()) { it.fingerprint() }
    return incoming.filter { it.fingerprint() !in seen }
}
