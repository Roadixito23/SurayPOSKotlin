package cl.suray.posbus.domain

import cl.suray.posbus.data.db.TransactionCategory
import cl.suray.posbus.data.db.TransactionEntity
import cl.suray.posbus.data.prefs.OpenTransaction

data class Totals(
    val pasajes: Double = 0.0,
    val correspondencias: Double = 0.0,
    val anulaciones: Double = 0.0,
) {
    val grandTotal: Double get() = pasajes + correspondencias + anulaciones
}

object Classifier {
    /**
     * Categoría que se guarda en la base (`TransactionEntity.fromReporteCaja`):
     * `startsWith`, **sensible** a mayúsculas.
     */
    fun categoryOf(nombre: String): TransactionCategory = when {
        nombre.startsWith(CANCELLATION_PREFIX) -> TransactionCategory.ANULACION
        nombre.startsWith(CARGO_PREFIX) -> TransactionCategory.CORRESPONDENCIA
        else -> TransactionCategory.PASAJE
    }

    /**
     * Clasificación del cierre automático (`AutoClosingService._createNewClosing`):
     * **insensible** a mayúsculas y además acepta `cargo:`/`correspondencia` en cualquier posición.
     * Es distinta a [categoryOf] a propósito: así funciona la app Flutter.
     */
    fun autoCloseBucket(nombre: String): TransactionCategory {
        val n = nombre.lowercase()
        return when {
            n.startsWith("anulación:") -> TransactionCategory.ANULACION
            n.contains("cargo:") || n.contains("correspondencia") -> TransactionCategory.CORRESPONDENCIA
            else -> TransactionCategory.PASAJE
        }
    }
}

object ClosingTotals {
    /** Totales de un cierre automático nuevo. */
    fun forAutoClose(transactions: List<OpenTransaction>): Totals {
        var p = 0.0; var c = 0.0; var a = 0.0
        for (t in transactions) when (Classifier.autoCloseBucket(t.nombre)) {
            TransactionCategory.ANULACION -> a += t.valor
            TransactionCategory.CORRESPONDENCIA -> c += t.valor
            TransactionCategory.PASAJE -> p += t.valor
        }
        return Totals(p, c, a)
    }

    /** Totales al fusionar: usan la categoría ya guardada en cada transacción. */
    fun forMerge(transactions: List<TransactionEntity>): Totals {
        var p = 0.0; var c = 0.0; var a = 0.0
        for (t in transactions) when (TransactionCategory.fromDb(t.category)) {
            TransactionCategory.ANULACION -> a += t.valor
            TransactionCategory.CORRESPONDENCIA -> c += t.valor
            TransactionCategory.PASAJE -> p += t.valor
        }
        return Totals(p, c, a)
    }

    /** Equivalente a `MergeService.calculateTotalsFromReporteCaja` (sensible a mayúsculas). */
    fun fromOpen(transactions: List<OpenTransaction>): Totals {
        var p = 0.0; var c = 0.0; var a = 0.0
        for (t in transactions) when (Classifier.categoryOf(t.nombre)) {
            TransactionCategory.ANULACION -> a += t.valor
            TransactionCategory.CORRESPONDENCIA -> c += t.valor
            TransactionCategory.PASAJE -> p += t.valor
        }
        return Totals(p, c, a)
    }
}

/** `TransactionEntity.fromReporteCaja`. [fallbackYear] cubre ventas antiguas sin año. */
fun OpenTransaction.toEntity(closingId: Long, fallbackYear: Int): TransactionEntity {
    val category = Classifier.categoryOf(nombre)
    return TransactionEntity(
        dailyClosingId = closingId,
        transactionId = id,
        nombre = nombre,
        valor = valor,
        comprobante = comprobante,
        dia = dia,
        mes = mes,
        ano = ano ?: fallbackYear.toString(),
        hora = hora,
        timestamp = timestamp,
        category = category.dbValue,
        isCancellation = category == TransactionCategory.ANULACION,
    )
}

fun TransactionEntity.toOpen(): OpenTransaction = OpenTransaction(
    id = transactionId, nombre = nombre, valor = valor, comprobante = comprobante,
    dia = dia, mes = mes, ano = ano, hora = hora, timestamp = timestamp,
)
