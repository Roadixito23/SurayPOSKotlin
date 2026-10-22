package cl.suray.posbus.print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument

/** Ancho de papel térmico: 58 mm en puntos PDF (1 pt = 1/72 in). */
const val PAPER_WIDTH_PT = 58 * 72f / 25.4f

/**
 * Dibuja de arriba hacia abajo en un ancho fijo. Con `canvas == null` solo mide, para conocer
 * la altura final de la página (el papel térmico es continuo; `PdfDocument` pide altura fija).
 */
class PdfPen(private val canvas: Canvas?, val width: Float = PAPER_WIDTH_PT) {
    var y = 0f
        private set

    private fun paint(size: Float, bold: Boolean = false, italic: Boolean = false, color: Int = Color.BLACK) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(
            Typeface.SANS_SERIF,
            when { bold && italic -> Typeface.BOLD_ITALIC; bold -> Typeface.BOLD; italic -> Typeface.ITALIC; else -> Typeface.NORMAL },
        )
    }

    private fun lineHeight(p: Paint) = p.fontMetrics.let { it.descent - it.ascent }

    fun space(h: Float) { y += h }

    /** Texto de una línea; si no cabe en [maxWidth], reduce el tamaño en vez de cortarlo. */
    private fun fitted(text: String, size: Float, bold: Boolean, italic: Boolean, maxWidth: Float, color: Int = Color.BLACK): Paint {
        val p = paint(size, bold, italic, color)
        val w = p.measureText(text)
        if (w > maxWidth) p.textSize = size * maxWidth / w
        return p
    }

    fun centered(text: String, size: Float, bold: Boolean = false, italic: Boolean = false, color: Int = Color.BLACK) {
        val p = fitted(text, size, bold, italic, width - 2 * PAD, color)
        canvas?.drawText(text, (width - p.measureText(text)) / 2, y - p.fontMetrics.ascent, p)
        y += lineHeight(p)
    }

    /** Dos textos en la misma línea, uno en cada borde. */
    fun spaced(left: String, right: String, size: Float, bold: Boolean = false) {
        val p = paint(size, bold)
        canvas?.drawText(left, PAD, y - p.fontMetrics.ascent, p)
        canvas?.drawText(right, width - PAD - p.measureText(right), y - p.fontMetrics.ascent, p)
        y += lineHeight(p)
    }

    /** Texto centrado dentro de un recuadro con borde. */
    fun boxedCentered(text: String, size: Float, bold: Boolean, padV: Float, padH: Float, fill: Int? = null) {
        val p = fitted(text, size, bold, false, width - 2 * PAD - 2 * padH)
        val tw = p.measureText(text)
        val h = lineHeight(p) + 2 * padV
        val w = tw + 2 * padH
        val left = (width - w) / 2
        canvas?.let { c ->
            val r = RectF(left, y, left + w, y + h)
            fill?.let { c.drawRect(r, Paint().apply { color = it; style = Paint.Style.FILL }) }
            c.drawRect(r, stroke(1f))
            c.drawText(text, left + padH, y + padV - p.fontMetrics.ascent, p)
        }
        y += h
    }

    fun bitmap(bmp: Bitmap, targetWidth: Float, align: Float = 0.5f) {
        val h = targetWidth * bmp.height / bmp.width
        val left = (width - targetWidth) * align
        canvas?.drawBitmap(bmp, null, RectF(left, y, left + targetWidth, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
        y += h
    }

    /** Fila cabecera-logo + recuadro del comprobante (ticket). */
    fun logoWithBox(logo: Bitmap, lines: List<Triple<String, Float, Boolean>>) {
        val logoW = width * 0.4f
        val logoH = logoW * logo.height / logo.width
        val boxW = width * 0.5f
        val padding = 2f
        val paints = lines.map { (t, s, b) -> fitted(t, s, b, false, boxW - 2 * padding) }
        val textH = paints.sumOf { lineHeight(it).toDouble() }.toFloat() + 2f
        val boxH = textH + 2 * padding
        canvas?.let { c ->
            c.drawBitmap(logo, null, RectF(0f, y, logoW, y + logoH), Paint(Paint.FILTER_BITMAP_FLAG))
            val left = width - boxW
            val top = y
            c.drawRect(RectF(left, top, left + boxW, top + boxH), stroke(1f))
            var ty = top + padding
            lines.forEachIndexed { i, (t, _, _) ->
                val p = paints[i]
                c.drawText(t, left + (boxW - p.measureText(t)) / 2, ty - p.fontMetrics.ascent, p)
                ty += lineHeight(p) + if (i == 1) 2f else 0f
            }
        }
        y += maxOf(logoH, boxH)
    }

    fun fillBar(text: String, size: Float, background: Int, textColor: Int, padV: Float) {
        val p = fitted(text, size, true, false, width - 2 * PAD, textColor)
        val h = lineHeight(p) + 2 * padV
        canvas?.let { c ->
            c.drawRect(0f, y, width, y + h, Paint().apply { color = background })
            c.drawText(text, (width - p.measureText(text)) / 2, y + padV - p.fontMetrics.ascent, p)
        }
        y += h
    }

    fun rule(height: Float) {
        canvas?.drawRect(0f, y, width, y + height, Paint().apply { color = Color.BLACK })
        y += height
    }

    /** Tabla con borde y columnas proporcionales ([flex]); texto recortado a la celda. */
    fun table(header: List<String>, rows: List<List<String>>, flex: List<Float>, rightAligned: Set<Int>) {
        val total = flex.sum()
        val widths = flex.map { it / total * (width - 2 * PAD) }
        val hp = paint(8f, bold = true)
        val bp = paint(9f)
        fun rowH(p: Paint) = lineHeight(p) + 8f
        val all = listOf(header to hp) + rows.map { it to bp }
        var top = y
        val grid = stroke(0.7f)
        canvas?.let { c ->
            all.forEach { (cells, p) ->
                var x = PAD
                val h = rowH(p)
                cells.forEachIndexed { i, text ->
                    val w = widths[i]
                    c.drawRect(RectF(x, top, x + w, top + h), grid)
                    c.save()
                    c.clipRect(x, top, x + w, top + h)
                    val tx = if (i in rightAligned) x + w - 4f - p.measureText(text) else x + 4f
                    c.drawText(text, tx, top + 4f - p.fontMetrics.ascent, p)
                    c.restore()
                    x += w
                }
                top += h
            }
        }
        y += all.sumOf { rowH(it.second).toDouble() }.toFloat()
    }

    /** Tabla centrada de celdas centradas, con cabecera gris (oferta en ruta). */
    fun centeredTable(header: List<String>, rows: List<List<String>>, flex: List<Float>, widthFactor: Float) {
        val tableW = width * widthFactor
        val left = (width - tableW) / 2
        val widths = flex.map { it / flex.sum() * tableW }
        val hp = paint(8f, bold = true)
        val bp = paint(9f)
        val all = listOf(header to hp) + rows.map { it to bp }
        var top = y
        canvas?.let { c ->
            all.forEachIndexed { r, (cells, p) ->
                val h = lineHeight(p) + 6f
                var x = left
                cells.forEachIndexed { i, text ->
                    if (r == 0) c.drawRect(RectF(x, top, x + widths[i], top + h), Paint().apply { color = Color.rgb(245, 245, 245) })
                    c.drawRect(RectF(x, top, x + widths[i], top + h), stroke(0.5f))
                    c.drawText(text, x + (widths[i] - p.measureText(text)) / 2, top + 3f - p.fontMetrics.ascent, p)
                    x += widths[i]
                }
                top += h
            }
            c.drawRect(RectF(left, y, left + tableW, top), stroke(1f))
        }
        y += all.sumOf { (lineHeight(it.second) + 6f).toDouble() }.toFloat()
    }

    sealed interface BoxItem {
        data class Gap(val h: Float) : BoxItem
        data class Line(val text: String, val size: Float, val bold: Boolean = false) : BoxItem
        /** Etiqueta pequeña seguida del valor en negrita, en la misma línea. */
        data class Inline(val label: String, val value: String) : BoxItem
        data class Pair(val left: String, val right: String, val size: Float) : BoxItem
    }

    private fun wrap(text: String, p: Paint, maxW: Float): List<String> {
        val out = mutableListOf<String>()
        var line = ""
        for (word in text.split(' ')) {
            var w = word
            while (p.measureText(w) > maxW && w.length > 1) { // palabra más ancha que la línea: se corta
                val n = (1..w.length).last { p.measureText(w.substring(0, it)) <= maxW }.coerceAtLeast(1)
                if (line.isNotEmpty()) { out += line; line = "" }
                out += w.substring(0, n); w = w.substring(n)
            }
            val candidate = if (line.isEmpty()) w else "$line $w"
            if (p.measureText(candidate) <= maxW) line = candidate else { out += line; line = w }
        }
        if (line.isNotEmpty()) out += line
        return out.ifEmpty { listOf("") }
    }

    /** Recuadro a todo el ancho con [items] adentro (texto que no cabe pasa a la línea siguiente). */
    fun box(items: List<BoxItem>, padding: Float = 6f) {
        val inner = width - 2 * PAD - 2 * padding
        class Row(val h: Float, val draw: (Canvas, Float, Float) -> Unit)
        val rows = mutableListOf<Row>()
        for (item in items) when (item) {
            is BoxItem.Gap -> rows += Row(item.h) { _, _, _ -> }
            is BoxItem.Line -> {
                val p = paint(item.size, item.bold)
                for (l in wrap(item.text, p, inner)) rows += Row(lineHeight(p)) { c, x, top -> c.drawText(l, x, top - p.fontMetrics.ascent, p) }
            }
            is BoxItem.Inline -> {
                val lp = paint(10f)
                val vp = fitted(item.value, 12f, true, false, inner - lp.measureText(item.label + " ") - 2f)
                rows += Row(maxOf(lineHeight(lp), lineHeight(vp))) { c, x, top ->
                    c.drawText(item.label, x, top - lp.fontMetrics.ascent, lp)
                    c.drawText(item.value, x + lp.measureText(item.label + " "), top - vp.fontMetrics.ascent, vp)
                }
            }
            is BoxItem.Pair -> {
                val p = paint(item.size)
                rows += Row(lineHeight(p)) { c, x, top ->
                    c.drawText(item.left, x, top - p.fontMetrics.ascent, p)
                    c.drawText(item.right, x + inner - p.measureText(item.right), top - p.fontMetrics.ascent, p)
                }
            }
        }
        val h = rows.sumOf { it.h.toDouble() }.toFloat() + 2 * padding
        canvas?.let { c ->
            c.drawRect(RectF(PAD, y, width - PAD, y + h), stroke(1f))
            var top = y + padding
            rows.forEach { it.draw(c, PAD + padding, top); top += it.h }
        }
        y += h
    }

    fun tapeRectangle(height: Float = 45f) {
        val p = fitted("COLOCAR CINTA", 16f, true, false, width - 2 * PAD - 12f)
        canvas?.let { c ->
            c.drawRect(RectF(PAD + 2f, y + 2f, width - PAD - 2f, y + height - 2f), stroke(4f))
            c.drawText("COLOCAR CINTA", (width - p.measureText("COLOCAR CINTA")) / 2, y + height / 2 - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2, p)
        }
        y += height
    }

    /** Tijera a la izquierda y línea punteada de corte. */
    fun cutLine(scissors: Bitmap) {
        val w = 12f
        val h = w * scissors.height / scissors.width
        canvas?.let { c ->
            c.drawBitmap(scissors, null, RectF(PAD, y, PAD + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
            val dash = Paint().apply {
                style = Paint.Style.STROKE; strokeWidth = 0.7f; color = Color.BLACK
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f, 3f), 0f)
            }
            c.drawLine(PAD + w + 4f, y + h / 2, width - PAD, y + h / 2, dash)
        }
        y += h
    }

    private fun stroke(w: Float) = Paint().apply { style = Paint.Style.STROKE; strokeWidth = w; color = Color.BLACK }

    private companion object { const val PAD = 3f }
}

/** PDF terminado con su altura en puntos (el papel continuo se declara con esa altura al imprimir). */
class RenderedPdf(val bytes: ByteArray, val heightPt: Int)

/** Mide el contenido, crea una página de esa altura y lo dibuja. */
fun renderPdf(draw: (PdfPen) -> Unit): RenderedPdf {
    val height = Math.ceil(PdfPen(null).also(draw).y.toDouble()).toInt().coerceAtLeast(1) + 8
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(Math.ceil(PAPER_WIDTH_PT.toDouble()).toInt(), height, 1).create())
    page.canvas.translate(0f, 4f)
    draw(PdfPen(page.canvas))
    doc.finishPage(page)
    val out = java.io.ByteArrayOutputStream()
    doc.writeTo(out)
    doc.close()
    return RenderedPdf(out.toByteArray(), height)
}
