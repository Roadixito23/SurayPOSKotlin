package cl.suray.posbus.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.suray.posbus.domain.DaySales
import cl.suray.posbus.domain.SalesStats
import cl.suray.posbus.domain.StatsFormat

private val Amber900 = Color(0xFFFF6F00)
private val Amber800 = Color(0xFFFF8F00)
private val Amber700 = Color(0xFFFFA000)
private val Amber300 = Color(0xFFFFD54F)
private val Grey600 = Color(0xFF757575)
private val TypeColors = listOf(Amber700, Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFF8E24AA), Color(0xFFFB8C00), Color(0xFF00897B))

/** "Estadísticas de Ventas": resumen de 30 días, gráfico semanal, ranking de pasajes y datos destacados. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: StatsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Estadísticas de Ventas") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            actions = { IconButton(vm::reload) { Icon(Icons.Default.Refresh, "Actualizar") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Amber800, titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White),
        )
    }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Amber800)
                state.failed -> Empty("No se pudieron cargar las estadísticas", vm::reload)
                state.stats.totalTransacciones == 0 -> Empty("Sin datos aún\nRegistra ventas desde la pantalla principal para ver las estadísticas aquí.", vm::reload)
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Title("Resumen General (30 días)")
                    Summary(state.stats)
                    Spacer(Modifier.height(8.dp))
                    Title("Ventas de la Semana")
                    WeeklyChart(state.weekly)
                    Spacer(Modifier.height(8.dp))
                    Title("Ventas por Tipo de Pasaje")
                    TypeRanking(state.stats)
                    Spacer(Modifier.height(8.dp))
                    Title("Datos Destacados")
                    Highlights(state.stats)
                }
            }
        }
    }
}

@Composable
private fun Empty(text: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, textAlign = TextAlign.Center, color = Grey600)
        Spacer(Modifier.height(20.dp))
        Button(onRetry, colors = ButtonDefaults.buttonColors(containerColor = Amber800)) { Text("Actualizar") }
    }
}

@Composable
private fun Title(text: String) = Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Amber900)

@Composable
private fun Summary(s: SalesStats) {
    val cards = listOf(
        Triple("Total Ventas", "$${StatsFormat.number(s.totalVentas)}", Color(0xFF43A047)),
        Triple("Transacciones", "${s.totalTransacciones}", Color(0xFF1E88E5)),
        Triple("Promedio Diario", "$${StatsFormat.number(s.promedioDiario)}", Color(0xFFFB8C00)),
        Triple("Días Activos", "${s.diasConVentas}", Color(0xFF8E24AA)),
    )
    cards.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { (title, value, color) ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.1f))
                        .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(12.dp),
                ) {
                    Text(title, fontSize = 12.sp, color = Grey600)
                    Spacer(Modifier.height(6.dp))
                    Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
                }
            }
        }
    }
}

@Composable
private fun WeeklyChart(weekly: List<DaySales>) {
    val max = weekly.maxOfOrNull { it.ventas }?.coerceAtLeast(1.0) ?: 1.0
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(16.dp)) {
        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            weekly.forEachIndexed { i, d ->
                val today = i == weekly.lastIndex
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text("$${StatsFormat.short(d.ventas)}", fontSize = 10.sp, color = Grey600, fontWeight = if (today) FontWeight.Bold else FontWeight.Normal)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.width(30.dp).height((d.ventas / max * 110).toFloat().coerceIn(4f, 110f).dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(if (today) Amber700 else Amber300),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            weekly.forEachIndexed { i, d ->
                val today = i == weekly.lastIndex
                Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.dia, fontSize = 11.sp, fontWeight = if (today) FontWeight.Bold else FontWeight.Normal, color = if (today) Amber900 else Grey600)
                    Text(d.fecha, fontSize = 9.sp, color = Color(0xFF9E9E9E))
                }
            }
        }
    }
}

@Composable
private fun TypeRanking(s: SalesStats) {
    val sorted = s.ventasPorTipo.entries.sortedByDescending { it.value }.take(6)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White)) {
        if (sorted.isEmpty()) Text("No hay datos de pasajes", Modifier.padding(16.dp), color = Grey600)
        sorted.forEachIndexed { i, (tipo, n) ->
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(TypeColors[i % TypeColors.size]), Alignment.Center) {
                    Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(tipo, fontWeight = FontWeight.Medium)
                    Text("$n ventas", fontSize = 12.sp, color = Grey600)
                }
                Text("$${StatsFormat.number(s.ingresosPorTipo[tipo] ?: 0.0)}", fontWeight = FontWeight.Bold, color = Color(0xFF388E3C))
            }
            if (i < sorted.lastIndex) HorizontalDivider()
        }
    }
}

@Composable
private fun Highlights(s: SalesStats) {
    val rows = listOf(
        "Mejor día" to "${s.mejorDia} ($${StatsFormat.number(s.maxVentas)})",
        "Pasaje más vendido" to "${s.pasajeMasVendido} (${s.cantidadPasajeMasVendido})",
        "Promedio semanal" to "$${StatsFormat.number(s.promedioSemanal)}",
        "Promedio Lun–Sáb" to "$${StatsFormat.number(s.promedioLunSab)}",
        "Promedio Domingos" to "$${StatsFormat.number(s.promedioDomingos)}",
    )
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF8E1)).border(1.dp, Color(0xFFFFE082), RoundedCornerShape(12.dp)).padding(16.dp)) {
        rows.forEachIndexed { i, (label, value) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), color = Grey600)
                Text(value, fontWeight = FontWeight.Bold, color = Color(0xFF424242), textAlign = TextAlign.End)
            }
            if (i < rows.lastIndex) HorizontalDivider(Modifier.padding(vertical = 12.dp))
        }
    }
}
