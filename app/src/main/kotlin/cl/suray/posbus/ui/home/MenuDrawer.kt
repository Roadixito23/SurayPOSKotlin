package cl.suray.posbus.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.suray.posbus.R
import cl.suray.posbus.ui.components.HeaderBrush
import cl.suray.posbus.ui.components.IconBadge
import cl.suray.posbus.ui.theme.Hemiheads
import cl.suray.posbus.ui.theme.SurayColors

/** Categorías del menú lateral, en el orden en que se muestran. */
enum class MenuSection(val title: String, val destinations: List<Destination>) {
    Caja("CAJA", listOf(Destination.Reports, Destination.Recovery)),
    Analisis("ANÁLISIS", listOf(Destination.Statistics)),
    Cobros("COBROS", listOf(Destination.Transfer)),
    Sistema("SISTEMA", listOf(Destination.Backup, Destination.Settings)),
}

@Composable
private fun Destination.painter(): Painter = when (this) {
    Destination.Reports -> painterResource(R.drawable.ic_receipt)
    Destination.Recovery -> painterResource(R.drawable.ic_history)
    Destination.Statistics -> painterResource(R.drawable.ic_chart)
    Destination.Transfer -> painterResource(R.drawable.ic_swap)
    Destination.Backup -> painterResource(R.drawable.ic_save)
    Destination.Settings -> rememberVectorPainter(Icons.Default.Settings)
}

@Composable
fun MenuDrawer(onNavigate: (Destination) -> Unit, onLogout: () -> Unit) {
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }

    ModalDrawerSheet(Modifier.width(310.dp), drawerContainerColor = SurayColors.Background) {
        Header(version)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp)) {
            MenuSection.entries.forEach { section ->
                Text(
                    section.title,
                    Modifier.padding(start = 12.dp, top = 10.dp, bottom = 2.dp),
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = SurayColors.Primary,
                )
                section.destinations.forEach { d -> MenuItem(d.label, d.painter(), onClick = { onNavigate(d) }) }
            }
            HorizontalDivider(Modifier.padding(horizontal = 4.dp, vertical = 10.dp), color = SurayColors.PrimaryLight)
            MenuItem("Cerrar Sesión", painterResource(R.drawable.ic_logout), danger = true, onClick = onLogout)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Header(version: String) {
    Row(
        Modifier.fillMaxWidth().background(HeaderBrush).statusBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.95f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Image(painterResource(R.drawable.logo), "Logo", Modifier.height(34.dp), contentScale = ContentScale.Fit)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("Menú", fontFamily = Hemiheads, fontSize = 22.sp, color = Color.White)
            Text(
                "Suray" + if (version.isNotEmpty()) " · v$version" else "",
                fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: Painter, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) SurayColors.Red700 else SurayColors.PrimaryDark
    val badge = if (danger) Color(0xFFFDECEA) else SurayColors.PrimaryLight
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(icon, tint = tint, background = badge, size = 38)
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (danger) SurayColors.Red700 else Color(0xFF263645))
    }
}
