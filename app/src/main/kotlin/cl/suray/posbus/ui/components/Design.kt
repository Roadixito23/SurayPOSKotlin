package cl.suray.posbus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.suray.posbus.ui.theme.SurayColors

/** Degradado de las barras superiores y cabeceras (azul oscuro → azul primario de la paleta). */
val HeaderBrush = Brush.linearGradient(listOf(SurayColors.PrimaryDark, SurayColors.Primary))

/** Ícono dentro de un cuadrado redondeado con fondo suave; la base visual del menú y de Ajustes. */
@Composable
fun IconBadge(painter: Painter, modifier: Modifier = Modifier, tint: Color = SurayColors.PrimaryDark, background: Color = SurayColors.PrimaryLight, size: Int = 40) {
    Box(modifier.size(size.dp).background(background, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Icon(painter, null, Modifier.size((size * 0.55f).dp), tint = tint)
    }
}

/** Tarjeta blanca con cabecera (ícono + título + subtítulo opcional) y contenido. */
@Composable
fun SectionCard(
    title: String,
    icon: Painter,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, size = 36)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SurayColors.PrimaryDark)
                    subtitle?.let { Text(it, fontSize = 12.sp, color = Color(0xFF6B7C8C)) }
                }
            }
            HorizontalDivider(color = SurayColors.PrimaryLight)
            content()
        }
    }
}
