package cl.suray.posbus.ui.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cl.suray.posbus.AppContainer
import cl.suray.posbus.R
import cl.suray.posbus.print.sunmi.SunmiDiagnostics
import cl.suray.posbus.print.sunmi.SunmiPrinterClient
import cl.suray.posbus.print.sunmi.SunmiReportPrinter
import cl.suray.posbus.ui.components.SectionCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Laboratorio de la impresión directa Sunmi (Fase 1 del plan `docs/plan-impresion-directa-sunmi.md`). */
class PrinterDiagViewModel(c: AppContainer) : ViewModel() {
    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val diag = SunmiDiagnostics(c.sunmi, SunmiReportPrinter(c.sunmi, c.prefs), c.clock) { line -> _log.update { (it + line).takeLast(MAX_LINES) } }

    fun note(line: String) = _log.update { (it + line).takeLast(MAX_LINES) }
    fun clear() { _log.value = emptyList() }

    private fun run(block: suspend SunmiDiagnostics.() -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            runCatching { diag.block() }.onFailure { note("✗ error inesperado: ${it.message}") }
            _busy.value = false
        }
    }

    fun info() = run { info() }
    fun testPage() = run { testPage() }
    fun bufferTest() = run { bufferTest() }
    fun probeUnknown() = run { probeUnknownCallbacks() }
    fun sampleReport() = run { sampleReport() }

    class Factory(private val c: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PrinterDiagViewModel(c) as T
    }

    private companion object { const val MAX_LINES = 400 }
}

@Composable
fun PrinterDiagTab(vm: PrinterDiagViewModel) {
    val log by vm.log.collectAsState()
    val busy by vm.busy.collectAsState()
    val context = LocalContext.current
    val scroll = rememberScrollState()

    // Avisos de estado que emite el servicio (papel, tapa, sobrecalentamiento…).
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { vm.note("📡 aviso: " + (i.action ?: "?").substringAfterLast('.')) }
        }
        val filter = IntentFilter().also { f -> SunmiPrinterClient.STATE_ACTIONS.forEach(f::addAction) }
        context.registerReceiver(receiver, filter)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    LaunchedEffect(log.size) { scroll.animateScrollTo(scroll.maxValue) }

    SectionCard("Impresora Sunmi directa", painterResource(R.drawable.ic_print), subtitle = "Laboratorio de pruebas (Fase 1)") {
        Text("Conecta con el servicio de impresión del equipo. La página de prueba gasta unos 25 cm de papel.", fontSize = 13.sp, color = Color(0xFF6B7C8C))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(vm::info, Modifier.weight(1f), enabled = !busy) { Text("Información", fontSize = 13.sp) }
            Button(vm::testPage, Modifier.weight(1f), enabled = !busy) { Text("Página de prueba", fontSize = 13.sp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(vm::bufferTest, Modifier.weight(1f), enabled = !busy) { Text("Modo buffer", fontSize = 13.sp) }
            OutlinedButton(vm::probeUnknown, Modifier.weight(1f), enabled = !busy) { Text("Sin identificar", fontSize = 13.sp) }
        }
        Button(vm::sampleReport, Modifier.fillMaxWidth(), enabled = !busy) { Text("Reporte de ejemplo (Fase 2)", fontSize = 13.sp) }
        OutlinedButton(vm::clear, Modifier.fillMaxWidth()) { Text("Limpiar registro") }
        Column(
            Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp).background(Color(0xFF10202E), RoundedCornerShape(10.dp))
                .verticalScroll(scroll).padding(10.dp),
        ) {
            if (log.isEmpty()) Text("Sin registros todavía.", color = Color(0xFF8FA3B5), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            log.forEach { Text(it, color = if (it.startsWith("✗")) Color(0xFFFF8A80) else Color(0xFFD6E6F6), fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
        }
    }
}
