package cl.suray.posbus.ui.home

import androidx.compose.ui.graphics.Color
import cl.suray.posbus.data.prefs.AppPrefs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

data class ButtonColors(val background: Color, val border: Color)

/** Colores de los botones: `button_colors_{weekday|sunday|otros}_N` o los predeterminados de Flutter. */
class ButtonPalette(private val custom: Map<String, ButtonColors>) {

    fun fare(index: Int, sunday: Boolean): ButtonColors =
        custom[(if (sunday) "sunday_" else "weekday_") + index] ?: defaultFare(index, sunday)

    fun otros(index: Int): ButtonColors =
        custom["otros_$index"] ?: ButtonColors(Orange.copy(alpha = ALPHA), Orange.copy(alpha = ALPHA))

    companion object {
        private const val ALPHA = 0.70f
        private val Red400 = Color(0xFFEF5350)
        private val Green400 = Color(0xFF66BB6A)
        private val Blue400 = Color(0xFF42A5F5)
        private val Grey400 = Color(0xFFBDBDBD)
        private val Blue300 = Color(0xFF64B5F6)
        private val Pink300 = Color(0xFFF06292)
        private val Yellow300 = Color(0xFFFFF176)
        private val Orange = Color(0xFFFF9800)

        private fun defaultFare(i: Int, sunday: Boolean): ButtonColors {
            val bg = if (sunday) listOf(Grey400, Red400, Green400, Red400, Red400, Color.White)
            else listOf(Red400, Green400, Blue400, Blue400, Green400, Color.White)
            val border = if (sunday) listOf(Blue300, Pink300, Yellow300, Pink300, Pink300, Color.Black)
            else List(6) { Color.Black }
            return ButtonColors(bg.getOrElse(i) { Color.Gray }.copy(alpha = ALPHA), border.getOrElse(i) { Color.Black })
        }

        fun load(prefs: AppPrefs): ButtonPalette {
            val out = HashMap<String, ButtonColors>()
            val keys = (0..5).flatMap { listOf("weekday_$it", "sunday_$it") } + listOf("otros_0", "otros_1")
            for (k in keys) {
                val raw = prefs.buttonColorsJson(k) ?: continue
                runCatching { parse(raw) }.getOrNull()?.let { out[k] = it }
            }
            return ButtonPalette(out)
        }

        private fun parse(raw: String): ButtonColors {
            val o = Json.parseToJsonElement(raw) as JsonObject
            fun n(key: String) = o[key]!!.jsonPrimitive.doubleOrNull!!.toInt().coerceIn(0, 255)
            val opacity = o["bgOpacity"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: ALPHA
            return ButtonColors(
                Color(n("bgRed"), n("bgGreen"), n("bgBlue"), (opacity * 255).toInt().coerceIn(0, 255)),
                Color(n("borderRed"), n("borderGreen"), n("borderBlue")),
            )
        }
    }
}
