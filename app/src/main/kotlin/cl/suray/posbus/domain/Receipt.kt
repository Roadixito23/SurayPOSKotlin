package cl.suray.posbus.domain

enum class Align(val code: Int) { Left(0), Center(1), Right(2) }

/** Línea de un recibo de texto para la impresora térmica (independiente del hardware: se puede probar en JVM). */
sealed interface ReceiptLine {
    /** [size] en puntos de la impresora (24 = ~32 columnas en 58 mm). */
    data class Text(
        val text: String,
        val size: Int = ReceiptLayouts.BASE_SIZE,
        val bold: Boolean = false,
        val align: Align = Align.Left,
        val inverted: Boolean = false,
    ) : ReceiptLine

    /** Tabla de una fila; [widths] en caracteres (suma ≤ [ReceiptLayouts.COLUMNS]). */
    data class Columns(val cells: List<String>, val widths: List<Int>, val aligns: List<Align>, val bold: Boolean = false) : ReceiptLine

    data class Feed(val lines: Int) : ReceiptLine
}

/** Estado de la impresora Sunmi (`updatePrinterState`). Verificado en el V1s: 1 y 4; los demás, según el AIDL oficial. */
object PrinterState {
    const val NORMAL = 1

    fun isReady(code: Int) = code == NORMAL

    fun describe(code: Int): String = when (code) {
        1 -> "lista"
        2 -> "iniciando"
        3 -> "sin comunicación con la impresora"
        4 -> "sin papel o con la tapa abierta"
        5 -> "sobrecalentada"
        6 -> "con la tapa abierta"
        7, 8 -> "con el cortador en falla"
        505 -> "no detectada"
        507 -> "actualizando firmware"
        else -> "en estado desconocido ($code)"
    }
}

object ReceiptLayouts {
    /** Columnas de texto a tamaño 24 en papel de 58 mm (verificado en el V1s con filas de 32 caracteres). */
    const val COLUMNS = 32
    const val BASE_SIZE = 24

    /** Altura de una línea de tamaño 24 en milímetros (24 puntos a 8 puntos/mm). */
    private const val LINE_MM = 3.0

    private val REPORT_WIDTHS = listOf(6, 3, 6, 7, 10)
    private val REPORT_ALIGNS = listOf(Align.Left, Align.Left, Align.Left, Align.Left, Align.Right)

    /** Cierre de caja en texto: mismo contenido que el PDF (`ReportLayouts`), pensado para 32 columnas. */
    fun report(layout: ReportLayout, endMarginPt: Float): List<ReceiptLine> {
        val out = mutableListOf<ReceiptLine>()
        out += ReceiptLine.Text("Transportes Suray Limitada", bold = true, align = Align.Center)
        out += ReceiptLine.Text("77.799.670-3", align = Align.Center)
        out += ReceiptLine.Text("Eusebio Ibar 630", align = Align.Center)
        out += ReceiptLine.Feed(1)
        out += ReceiptLine.Text(centerPad("CIERRE DE CAJA", COLUMNS), bold = true, inverted = true)
        out += ReceiptLine.Text(layout.dayName, size = 30, bold = true, align = Align.Center)
        out += ReceiptLine.Columns(listOf("ID: ${layout.terminalId}", layout.dateTime), listOf(10, 22), listOf(Align.Left, Align.Right))
        out += ReceiptLine.Text(layout.totalLine, bold = true, align = Align.Center)
        out += ReceiptLine.Feed(1)

        for (s in layout.sections) {
            out += ReceiptLine.Text(s.title, bold = true, align = Align.Center)
            if (s.rows.isEmpty()) {
                out += ReceiptLine.Text("No hay transacciones", align = Align.Center)
            } else {
                out += fit(ReceiptLine.Columns(listOf("N°C", "Día", "Hora", "Tipo", "Valor"), REPORT_WIDTHS, REPORT_ALIGNS, bold = true))
                s.rows.forEach { r -> out += fit(ReceiptLine.Columns(listOf(r.comprobante, r.dia, r.hora, r.tipo, r.valor), REPORT_WIDTHS, REPORT_ALIGNS)) }
                out += ReceiptLine.Text(s.totalLine, bold = true, align = Align.Center)
            }
            out += ReceiptLine.Feed(1)
        }
        if (!layout.hasCancellations) out += ReceiptLine.Text("Sin anulaciones el día de Hoy", align = Align.Center)

        out += ReceiptLine.Feed(marginLines(endMarginPt))
        out += ReceiptLine.Text(" ".repeat(COLUMNS), inverted = true) // barra final: guía de corte
        out += ReceiptLine.Feed(2)
        return out
    }

    /** Líneas en blanco equivalentes al margen final del PDF (en puntos), mínimo 1. */
    fun marginLines(points: Float): Int = Math.ceil(points * 25.4 / 72.0 / LINE_MM).toInt().coerceAtLeast(1)

    /** Recorta cada celda a su ancho: si el texto excede la columna, el servicio lo partiría y desordenaría la tabla. */
    fun fit(c: ReceiptLine.Columns) = c.copy(cells = c.cells.mapIndexed { i, t -> t.take(c.widths.getOrElse(i) { COLUMNS }) })

    fun centerPad(text: String, width: Int): String {
        val t = text.take(width)
        val left = (width - t.length) / 2
        return " ".repeat(left) + t + " ".repeat(width - t.length - left)
    }
}
