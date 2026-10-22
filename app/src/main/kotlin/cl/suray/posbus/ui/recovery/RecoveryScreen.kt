package cl.suray.posbus.ui.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.suray.posbus.domain.PdfFileInfo
import cl.suray.posbus.domain.ReportFiles

/** "Recuperación de Reportes": PDFs de cierres guardados, con búsqueda y reimpresión. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryScreen(vm: RecoveryViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recuperación de Reportes") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Total", "${state.stats.total}", Modifier.weight(1f))
                StatCard("Últimos 7 días", "${state.stats.lastWeek}", Modifier.weight(1f))
                StatCard("Espacio", "${state.stats.totalSizeMb} MB", Modifier.weight(1f))
            }
            OutlinedTextField(
                state.query, vm::setQuery, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                label = { Text("Buscar reporte") }, singleLine = true,
            )
            when {
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                state.visible.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(if (state.query.isBlank()) "No hay reportes guardados" else "Sin resultados")
                }
                else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.visible, key = { it.name }) { f -> ReportRow(f, enabled = !state.printing) { vm.print(f) } }
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(title, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ReportRow(f: PdfFileInfo, enabled: Boolean, onPrint: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val n = ReportFiles.number(f.name)
                Text(ReportFiles.title(f) + if (n.isEmpty()) "" else "  ($n)", fontWeight = FontWeight.Bold)
                Text("${ReportFiles.dateTime(f)} · ${ReportFiles.size(f.sizeBytes)}", fontSize = 12.sp)
            }
            TextButton(onPrint, enabled = enabled) {
                Text("Imprimir")
            }
        }
    }
}
