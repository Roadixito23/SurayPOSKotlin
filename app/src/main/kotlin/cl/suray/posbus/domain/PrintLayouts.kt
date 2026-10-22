package cl.suray.posbus.domain

import cl.suray.posbus.data.prefs.OpenTransaction

/** Pesos chilenos con punto de miles (`3.600`), sin depender de la configuración regional del equipo. */
fun formatClp(value: Double): String = formatClp(Math.round(value))

fun formatClp(value: Long): String {
    val digits = Math.abs(value).toString()
    val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
    return if (value < 0) "-$grouped" else grouped
}

/** Texto del ticket normal (58 mm). La parte gráfica vive en `print/`. */
data class TicketLayout(
    val numberLine: String,
    val tariffLine: String,
    val tipo: String,
    val valueLine: String,
    val time: String,
    val date: String,
    val reprintedAt: String?,
) {
    val isReprint: Boolean get() = reprintedAt != null
}

data class OfferTicketLayout(
    val numberLine: String,
    val rows: List<Pair<String, String>>,
    val totalLine: String,
    val time: String,
    val date: String,
    val reprintedAt: String?,
) {
    val isReprint: Boolean get() = reprintedAt != null
}

enum class CargoCopy { Client, Cargo }

/** Una copia de cargo; [boxes] y demás textos se dibujan en `print/`. */
data class CargoTicketLayout(
    val numberLine: String,
    val caption: String,
    val destino: String,
    val articulo: String,
    val precioLine: String,
    val destinatario: String,
    val telefono: String?,
    val fecha: String,
    val hora: String,
    val comprobante: String,
    val copy: CargoCopy,
)

object TicketLayouts {
    private fun dateTime(nowMillis: Long): Pair<String, String> {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
        val time = "%02d:%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE), c.get(java.util.Calendar.SECOND))
        val date = "%02d/%02d/%04d".format(c.get(java.util.Calendar.DAY_OF_MONTH), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.YEAR))
        return time to date
    }

    fun offer(entries: List<cl.suray.posbus.data.prefs.OfferEntry>, comprobante: String, nowMillis: Long, reprint: Boolean): OfferTicketLayout {
        val (time, date) = dateTime(nowMillis)
        return OfferTicketLayout(
            numberLine = "N° $comprobante",
            rows = entries.map { Offers.quantity(it).toString() to "$${formatClp(Offers.price(it))}" },
            totalLine = "Total: $${formatClp(Offers.total(entries))}",
            time = time,
            date = date,
            reprintedAt = if (reprint) "$date $time" else null,
        )
    }

    fun cargo(data: cl.suray.posbus.data.prefs.CargoData, comprobante: String, copy: CargoCopy, nowMillis: Long, reprint: Boolean): CargoTicketLayout {
        val (time, date) = dateTime(nowMillis)
        val who = if (copy == CargoCopy.Client) "COPIA CLIENTE" else "COPIA CARGA"
        return CargoTicketLayout(
            numberLine = "N° $comprobante",
            caption = if (reprint) "$who (REIMPRESIÓN)" else who,
            destino = data.destino,
            articulo = data.articulo,
            precioLine = "$${formatClp(data.precio)}",
            destinatario = data.destinatario,
            telefono = data.telefono.ifEmpty { null },
            fecha = date,
            hora = time,
            comprobante = comprobante,
            copy = copy,
        )
    }

    fun regular(
        nombre: String,
        valor: Double,
        comprobante: String,
        sundayRate: Boolean,
        nowMillis: Long,
        reprint: Boolean,
    ): TicketLayout {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
        val time = "%02d:%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE), c.get(java.util.Calendar.SECOND))
        val date = "%02d/%02d/%04d".format(c.get(java.util.Calendar.DAY_OF_MONTH), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.YEAR))
        return TicketLayout(
            numberLine = "N° $comprobante",
            tariffLine = if (sundayRate) "TARIFA DOMINGO/FERIADO" else "TARIFA LUNES A SÁBADO",
            tipo = nombre,
            valueLine = "Valor: $${formatClp(valor)}",
            time = time,
            date = date,
            reprintedAt = if (reprint) "$date $time" else null,
        )
    }
}

data class ReportRow(val comprobante: String, val dia: String, val hora: String, val tipo: String, val valor: String)

data class ReportSection(val title: String, val rows: List<ReportRow>, val totalLine: String)

data class ReportLayout(
    val dayName: String,
    val terminalId: Int,
    val dateTime: String,
    val totalLine: String,
    val sections: List<ReportSection>,
    val hasCancellations: Boolean,
)

/** Reglas del PDF de cierre de Flutter (`pdfReport_generator.dart`): categorías, abreviaturas y totales. */
object ReportLayouts {
    private val DEFAULT_ABBREVIATIONS = mapOf(
        "Público General" to "PG",
        "Escolar General" to "Esc.",
        "Adulto Mayor" to "AM",
        "Escolar Intermedio" to "Int.E",
        "Intermedio hasta 15 Km" to "Int.15",
        "Int. hasta 15 Km" to "Int.15",
        "Intermedio hasta 50 Km" to "Int.50",
        "Int. hasta 50 Km" to "Int.50",
        "Oferta Ruta" to "OR",
        "Cargo" to "Cargo",
    )

    /** [abbreviations] reemplaza (por nombre) a los valores por defecto. */
    fun build(report: CashReport, terminalId: Int, abbreviations: Map<String, String> = emptyMap()): ReportLayout {
        val sorted = report.transactions.sortedBy { it.id }

        fun isCancel(t: OpenTransaction) = t.nombre.startsWith(CANCELLATION_PREFIX)
        fun isOtros(n: String) = n.startsWith("Cargo:") || n == "Oferta Ruta"
        fun isIntermedio(n: String): Boolean {
            val l = n.lowercase()
            return l.contains("int. hasta") || (l.contains("escolar") && l.contains("intermedio"))
        }

        val cancels = sorted.filter(::isCancel)
        val otros = sorted.filter { !isCancel(it) && isOtros(it.nombre) }
        val intermedio = sorted.filter { !isCancel(it) && !isOtros(it.nombre) && isIntermedio(it.nombre) }
        val pasajes = sorted.filter { !isCancel(it) && !isOtros(it.nombre) && !isIntermedio(it.nombre) }

        fun section(title: String, list: List<OpenTransaction>, alwaysShow: Boolean): ReportSection? {
            if (list.isEmpty() && !alwaysShow) return null
            val negative = title == "Anulaciones"
            val sign = if (negative) "-" else ""
            val rows = list.map {
                ReportRow(
                    comprobante = shortComprobante(it.comprobante),
                    dia = it.dia,
                    hora = it.hora,
                    tipo = if (isCancel(it)) "Anula" else abbreviation(it.nombre, abbreviations),
                    valor = "$sign$${formatClp(Math.abs(it.valor))}",
                )
            }
            val total = list.sumOf { it.valor }
            return ReportSection(title, rows, "Total: $sign$${formatClp(Math.abs(total))}")
        }

        val d = report.date
        return ReportLayout(
            dayName = DAYS_ES_UPPER[d.day.dayOfWeek - 1],
            terminalId = terminalId,
            dateTime = "%02d/%02d/%04d %02d:%02d".format(d.day.day, d.day.month, d.day.year, d.hour, d.minute),
            totalLine = "Total de Ingresos: $${formatClp(report.total)}",
            sections = listOfNotNull(
                section("Pasajeros", pasajes, alwaysShow = true),
                section("Intermedio", intermedio, alwaysShow = false),
                section("Otros", otros, alwaysShow = false),
                section("Anulaciones", cancels, alwaysShow = false),
            ),
            hasCancellations = cancels.isNotEmpty(),
        )
    }

    /** `01-000123` → `123`. */
    fun shortComprobante(c: String): String {
        val body = c.removePrefix("01-")
        return if (body.isNotEmpty() && body.all(Char::isDigit)) body.trimStart('0').ifEmpty { "0" } else c
    }

    /** [overrides] (preferencias del usuario) tiene prioridad sobre los valores por defecto. */
    fun abbreviation(nombre: String, overrides: Map<String, String>): String {
        val abbr = DEFAULT_ABBREVIATIONS + overrides
        val l = nombre.lowercase()
        if (l.contains("escolar") && (l.contains("intermedio") || l.contains("int"))) return abbr["Escolar Intermedio"] ?: "Int.E"
        if (nombre.startsWith("Cargo:")) return abbr["Cargo"] ?: "Cargo"
        abbr[nombre]?.let { return it }
        if (l.contains("público") || l.contains("general")) return abbr["Público General"] ?: "PG"
        if (l.contains("escolar") && !l.contains("intermedio")) return abbr["Escolar General"] ?: "Esc."
        if (l.contains("adulto") || l.contains("mayor")) return abbr["Adulto Mayor"] ?: "AM"
        if (l.contains("intermedio") || l.contains("int")) {
            if (l.contains("15")) return abbr["Intermedio hasta 15 Km"] ?: "Int.15"
            if (l.contains("50")) return abbr["Intermedio hasta 50 Km"] ?: "Int.50"
        }
        if (l.contains("oferta") || l.contains("ruta")) return abbr["Oferta Ruta"] ?: "OR"
        return nombre
    }
}
