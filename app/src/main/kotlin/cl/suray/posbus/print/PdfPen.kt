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

    private fun stroke(w: Float) = Paint().apply { style = Paint.Style.STROKE; strokeWidth = w; color = Color.BLACK }

    private companion object { const val PAD = 3f }
}

/** Mide el contenido, crea una página de esa altura y lo dibuja. */
fun renderPdf(draw: (PdfPen) -> Unit): PdfDocument {
    val height = Math.ceil(PdfPen(null).also(draw).y.toDouble()).toInt().coerceAtLeast(1) + 8
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(Math.ceil(PAPER_WIDTH_PT.toDouble()).toInt(), height, 1).create())
    page.canvas.translate(0f, 4f)
    draw(PdfPen(page.canvas))
    doc.finishPage(page)
    return doc
}
