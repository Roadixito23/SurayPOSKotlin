package cl.suray.posbus.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

enum class ButtonKind(val key: String) { Weekday("weekday"), Sunday("sunday"), Otros("otros") }

/** Color de un botón en RGB 0-255 y opacidad del fondo (formato de `button_colors_*` de Flutter). */
data class ButtonColorSpec(
    val bgR: Int, val bgG: Int, val bgB: Int, val bgOpacity: Float,
    val borderR: Int, val borderG: Int, val borderB: Int,
) {
    fun toJson(): String = JsonObject(
        mapOf(
            "bgRed" to JsonPrimitive(bgR.toDouble()), "bgGreen" to JsonPrimitive(bgG.toDouble()), "bgBlue" to JsonPrimitive(bgB.toDouble()),
            "bgOpacity" to JsonPrimitive(bgOpacity.toString().toDouble()),
            "borderRed" to JsonPrimitive(borderR.toDouble()), "borderGreen" to JsonPrimitive(borderG.toDouble()), "borderBlue" to JsonPrimitive(borderB.toDouble()),
        ),
    ).toString()

    companion object {
        const val DEFAULT_OPACITY = 0.70f
        fun storageKey(kind: ButtonKind, index: Int) = "${kind.key}_$index"

        /** `null` si el JSON está dañado; los canales se limitan a 0-255 y la opacidad a 0-1. */
        fun fromJson(raw: String): ButtonColorSpec? = runCatching {
            val o = Json.parseToJsonElement(raw) as JsonObject
            fun n(k: String) = (o[k] as JsonPrimitive).doubleOrNull!!.toInt().coerceIn(0, 255)
            ButtonColorSpec(
                n("bgRed"), n("bgGreen"), n("bgBlue"),
                ((o["bgOpacity"] as? JsonPrimitive)?.doubleOrNull?.toFloat() ?: DEFAULT_OPACITY).coerceIn(0f, 1f),
                n("borderRed"), n("borderGreen"), n("borderBlue"),
            )
        }.getOrNull()
    }
}

/** Colores de fábrica de cada botón (los mismos que muestra Home sin personalización). */
object ButtonColorDefaults {
    private fun rgb(c: Int) = Triple((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
    private const val RED = 0xEF5350
    private const val GREEN = 0x66BB6A
    private const val BLUE = 0x42A5F5
    private const val GREY = 0xBDBDBD
    private const val WHITE = 0xFFFFFF
    private const val BLACK = 0x000000
    private const val ORANGE = 0xFF9800

    private val WEEKDAY_BG = listOf(RED, GREEN, BLUE, BLUE, GREEN, WHITE)
    private val SUNDAY_BG = listOf(GREY, RED, GREEN, RED, RED, WHITE)
    private val SUNDAY_BORDER = listOf(0x64B5F6, 0xF06292, 0xFFF176, 0xF06292, 0xF06292, BLACK)

    fun spec(kind: ButtonKind, index: Int): ButtonColorSpec {
        val (bg, border) = when (kind) {
            ButtonKind.Weekday -> WEEKDAY_BG.getOrElse(index) { 0x9E9E9E } to BLACK
            ButtonKind.Sunday -> SUNDAY_BG.getOrElse(index) { 0x9E9E9E } to SUNDAY_BORDER.getOrElse(index) { BLACK }
            ButtonKind.Otros -> ORANGE to ORANGE
        }
        val (r, g, b) = rgb(bg)
        val (br, bgn, bb) = rgb(border)
        return ButtonColorSpec(r, g, b, ButtonColorSpec.DEFAULT_OPACITY, br, bgn, bb)
    }
}
