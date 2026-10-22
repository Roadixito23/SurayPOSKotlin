package cl.suray.posbus.domain

import cl.suray.posbus.data.db.AppDatabase
import cl.suray.posbus.data.prefs.OpenTransaction

data class SalesStats(
    val totalVentas: Double = 0.0,
    val totalPasajes: Double = 0.0,
    val totalCorrespondencias: Double = 0.0,
    val totalAnulaciones: Double = 0.0,
    val totalTransacciones: Int = 0,
    val diasConVentas: Int = 0,
    val promedioDiario: Double = 0.0,
    val promedioTransacciones: Double = 0.0,
    val mejorDia: String = "-",
    val maxVentas: Double = 0.0,
    val pasajeMasVendido: String = "-",
    val cantidadPasajeMasVendido: Int = 0,
    /** Clave `dd/mm/yyyy`; conserva el orden de inserción. */
    val ventasPorDia: Map<String, Double> = emptyMap(),
    val ventasPorTipo: Map<String, Int> = emptyMap(),
    val ingresosPorTipo: Map<String, Double> = emptyMap(),
    val promedioSemanal: Double = 0.0,
    val promedioLunSab: Double = 0.0,
    val promedioDomingos: Double = 0.0,
)

data class DaySales(val dia: String, val fecha: String, val ventas: Double)

/** Port de `StatisticsService`: el cálculo no toca almacenamiento. */
object Statistics {

    fun sales(all: List<OpenTransaction>, today: LocalDay, days: Int = 30): SalesStats {
        if (all.isEmpty()) return SalesStats()
        val cutoff = today.minusDays(days)

        val inRange = all.filter { t ->
            val d = t.dia.toIntOrNull()
            val m = t.mes.toIntOrNull()
            val y = (t.ano ?: today.year.toString()).toIntOrNull()
            if (d == null || m == null || y == null) true else LocalDay(y, m, d) >= cutoff
        }
        if (inRange.isEmpty()) return SalesStats()

        var totalVentas = 0.0
        var totalCorrespondencias = 0.0
        var totalAnulaciones = 0.0
        var totalTransacciones = 0
        val ventasPorDia = LinkedHashMap<String, Double>()
        val ventasPorTipo = LinkedHashMap<String, Int>()
        val ingresosPorTipo = LinkedHashMap<String, Double>()
        val weekdayByDate = HashMap<String, Int>()

        for (t in inRange) {
            val ano = t.ano ?: today.year.toString()
            val dateKey = "${t.dia}/${t.mes}/$ano"

            if (t.nombre.startsWith(CANCELLATION_PREFIX)) {
                totalAnulaciones += kotlin.math.abs(t.valor)
                continue
            }
            totalVentas += t.valor
            totalTransacciones++
            ventasPorDia[dateKey] = (ventasPorDia[dateKey] ?: 0.0) + t.valor

            if (dateKey !in weekdayByDate) {
                val d = t.dia.toIntOrNull(); val m = t.mes.toIntOrNull(); val y = ano.toIntOrNull()
                if (d != null && m != null && y != null) weekdayByDate[dateKey] = LocalDay(y, m, d).dayOfWeek
            }

            if (t.nombre.lowercase().startsWith("cargo")) {
                totalCorrespondencias += t.valor
            } else {
                ventasPorTipo[t.nombre] = (ventasPorTipo[t.nombre] ?: 0) + 1
                ingresosPorTipo[t.nombre] = (ingresosPorTipo[t.nombre] ?: 0.0) + t.valor
            }
        }

        val diasConVentas = ventasPorDia.size
        val promedioDiario = if (diasConVentas > 0) totalVentas / diasConVentas else 0.0

        var mejorDia = "-"; var maxVentas = 0.0
        for ((dia, v) in ventasPorDia) if (v > maxVentas) { maxVentas = v; mejorDia = dia }

        var pasajeMasVendido = "-"; var maxCantidad = 0
        for ((tipo, n) in ventasPorTipo) if (n > maxCantidad) { maxCantidad = n; pasajeMasVendido = tipo }

        fun average(range: IntRange): Double {
            val days = ventasPorDia.filter { (weekdayByDate[it.key] ?: 0) in range }
            return if (days.isNotEmpty()) days.values.sum() / days.size else 0.0
        }

        return SalesStats(
            totalVentas = totalVentas,
            totalPasajes = totalVentas - totalCorrespondencias,
            totalCorrespondencias = totalCorrespondencias,
            totalAnulaciones = totalAnulaciones,
            totalTransacciones = totalTransacciones,
            diasConVentas = diasConVentas,
            promedioDiario = promedioDiario,
            promedioTransacciones = if (diasConVentas > 0) totalTransacciones.toDouble() / diasConVentas else 0.0,
            mejorDia = mejorDia,
            maxVentas = maxVentas,
            pasajeMasVendido = pasajeMasVendido,
            cantidadPasajeMasVendido = maxCantidad,
            ventasPorDia = ventasPorDia,
            ventasPorTipo = ventasPorTipo,
            ingresosPorTipo = ingresosPorTipo,
            promedioSemanal = promedioDiario,
            promedioLunSab = average(1..6),
            promedioDomingos = average(7..7),
        )
    }

    /** Ventas de los últimos 7 días terminando en [today] (anulaciones y valores ≤ 0 se omiten). */
    fun weekly(all: List<OpenTransaction>, today: LocalDay): List<DaySales> {
        val byDate = HashMap<String, Double>()
        for (t in all) {
            if (t.nombre.startsWith(CANCELLATION_PREFIX) || t.valor <= 0) continue
            val key = "${t.ano ?: today.year}-${t.mes.padStart(2, '0')}-${t.dia.padStart(2, '0')}"
            byDate[key] = (byDate[key] ?: 0.0) + t.valor
        }
        return (0 until 7).map { i ->
            val d = today.minusDays(6 - i)
            DaySales(DAYS_ES_SHORT[d.dayOfWeek - 1], "${d.day}/${d.month}", byDate[d.iso()] ?: 0.0)
        }
    }
}

/** Ventas cerradas (Room) + caja abierta, para alimentar [Statistics]. */
class StatisticsRepository(private val db: AppDatabase, private val register: CashRegister) {
    suspend fun allTransactions(): List<OpenTransaction> {
        val closed = runCatching { db.transactionDao().getAllByTimestamp().map { it.toOpen() } }
            .getOrDefault(emptyList())
        return closed + register.all
    }
}
