package cl.suray.posbus.ui.home

import androidx.compose.ui.graphics.Color
import cl.suray.posbus.data.prefs.AppPrefs
import cl.suray.posbus.domain.ButtonColorDefaults
import cl.suray.posbus.domain.ButtonColorSpec
import cl.suray.posbus.domain.ButtonKind

data class ButtonColors(val background: Color, val border: Color)

fun ButtonColorSpec.toColors() = ButtonColors(
    Color(bgR, bgG, bgB, (bgOpacity * 255).toInt().coerceIn(0, 255)),
    Color(borderR, borderG, borderB),
)

/** Colores de los botones: `button_colors_{weekday|sunday|otros}_N` o los predeterminados de Flutter. */
class ButtonPalette(private val custom: Map<String, ButtonColorSpec>) {

    fun fare(index: Int, sunday: Boolean): ButtonColors = spec(if (sunday) ButtonKind.Sunday else ButtonKind.Weekday, index).toColors()

    fun otros(index: Int): ButtonColors = spec(ButtonKind.Otros, index).toColors().let {
        // Sin personalizar, el borde de Oferta/Cargo es el naranja con la misma transparencia del fondo.
        if (custom.containsKey(ButtonColorSpec.storageKey(ButtonKind.Otros, index))) it else it.copy(border = it.background)
    }

    private fun spec(kind: ButtonKind, index: Int) =
        custom[ButtonColorSpec.storageKey(kind, index)] ?: ButtonColorDefaults.spec(kind, index)

    companion object {
        fun load(prefs: AppPrefs): ButtonPalette {
            val out = HashMap<String, ButtonColorSpec>()
            for (kind in ButtonKind.entries) {
                val count = if (kind == ButtonKind.Otros) 2 else 6
                for (i in 0 until count) {
                    val key = ButtonColorSpec.storageKey(kind, i)
                    prefs.buttonColorsJson(key)?.let(ButtonColorSpec::fromJson)?.let { out[key] = it }
                }
            }
            return ButtonPalette(out)
        }
    }
}
