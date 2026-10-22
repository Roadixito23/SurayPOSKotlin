package cl.suray.posbus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import cl.suray.posbus.R

/** Paleta de `lib/theme/app_theme.dart`. */
object SurayColors {
    val Primary = Color(0xFF4F8FC0)
    val PrimaryDark = Color(0xFF395B7F)
    val PrimaryLight = Color(0xFFD6E6F6)
    val Secondary = Color(0xFF6FCF97)
    val SecondaryDark = Color(0xFF3B7A57)
    val Accent = Color(0xFFF2C94C)
    val Error = Color(0xFFE57373)
    val Background = Color(0xFFF8FAFB)

    // Login/Home de Flutter usan la familia ámbar
    val Amber700 = Color(0xFFFFA000)
    val Amber800 = Color(0xFFFF8F00)
    val Amber900 = Color(0xFFFF6F00)
    val Amber100 = Color(0xFFFFECB3)
    val Orange = Color(0xFFFF9800)
    val Orange800 = Color(0xFFEF6C00)
    val Red700 = Color(0xFFD32F2F)
    val Red900 = Color(0xFFB71C1C)
}

val Hemiheads = FontFamily(Font(R.font.hemiheads))

@Composable
fun SurayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = SurayColors.Primary,
            secondary = SurayColors.Secondary,
            tertiary = SurayColors.Accent,
            error = SurayColors.Error,
            background = SurayColors.Background,
            surface = Color.White,
            // Diálogos, hojas y tarjetas con tonos de la paleta (sin el lila por defecto de Material 3).
            surfaceContainerHigh = Color.White,
            surfaceContainerHighest = SurayColors.PrimaryLight,
            surfaceContainer = SurayColors.Background,
            surfaceVariant = SurayColors.PrimaryLight,
            primaryContainer = SurayColors.PrimaryLight,
            secondaryContainer = SurayColors.PrimaryLight,
            onSecondaryContainer = SurayColors.PrimaryDark,
            onPrimaryContainer = SurayColors.PrimaryDark,
        ),
        content = content,
    )
}
