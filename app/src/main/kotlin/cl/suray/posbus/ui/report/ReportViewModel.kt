package cl.suray.posbus.ui.report

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cl.suray.posbus.AppContainer
import cl.suray.posbus.data.prefs.OpenTransaction
import cl.suray.posbus.domain.CANCELLATION_PREFIX
import cl.suray.posbus.domain.CloseResult
import cl.suray.posbus.domain.LocalDay
import cl.suray.posbus.ui.home.ButtonPalette
import cl.suray.posbus.ui.home.MessageKind
import cl.suray.posbus.ui.home.UiMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Categoría visual de una fila: define franja, ícono y fondo. */
enum class RowKind { Normal, PastDay, Cancellation }

data class ReportRow(val tx: OpenTransaction, val kind: RowKind, val color: Color)

data class ReportUiState(
    val rows: List<ReportRow> = emptyList(),
    val total: Double = 0.0,
    val cardTotal: Double = 0.0,
    val newestFirst: Boolean = true,
    val closing: Boolean = false,
    val statusMessage: String = "",
) {
    val count: Int get() = rows.size
    val isEmpty: Boolean get() = rows.isEmpty()
    val cashTotal: Double get() = total - cardTotal
}

class ReportViewModel(private val c: AppContainer) : ViewModel() {

    private val newestFirst = MutableStateFlow(true)
    private val closing = MutableStateFlow(false)
    private val status = MutableStateFlow("")
    private val palette = ButtonPalette.load(c.prefs)

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    val state: StateFlow<ReportUiState> = combine(c.register.changes, newestFirst, closing, status) { open, newest, busy, msg ->
        val today = LocalDay.of(c.clock.nowMillis())
        val dia = "%02d".format(today.day)
        val mes = "%02d".format(today.month)
        val sunday = today.dayOfWeek == 7
        val sorted = if (newest) open.sortedByDescending { it.id } else open.sortedBy { it.id }
        ReportUiState(
            rows = sorted.map { t ->
                val kind = when {
                    t.dia != dia || t.mes != mes -> RowKind.PastDay
                    t.nombre.startsWith(CANCELLATION_PREFIX) -> RowKind.Cancellation
                    else -> RowKind.Normal
                }
                ReportRow(t, kind, colorFor(t.nombre, sunday))
            },
            total = open.sumOf { it.valor },
            cardTotal = c.cardPayments.totalToday(),
            newestFirst = newest,
            closing = busy,
            statusMessage = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReportUiState())

    fun toggleSort() { newestFirst.value = !newestFirst.value }

    fun closeRegister() {
        if (closing.value) return
        viewModelScope.launch {
            closing.value = true
            status.value = "Generando reporte..."
            val text = when (val r = c.closeRegister()) {
                CloseResult.Empty -> UiMessage("No hay transacciones para generar reporte", MessageKind.Warning)
                is CloseResult.Done -> UiMessage("Reporte generado e impreso correctamente", MessageKind.Success)
                is CloseResult.PrintFailed -> UiMessage("Error al imprimir el reporte: ${r.cause.message}. La caja sigue abierta.", MessageKind.Error)
                is CloseResult.SaveFailed -> UiMessage("El reporte se imprimió pero no se pudo guardar el cierre: ${r.cause.message}. La caja sigue abierta.", MessageKind.Error)
            }
            closing.value = false
            status.value = ""
            _messages.tryEmit(text)
        }
    }

    /** Color por tipo: el de su botón (personalizado o por defecto), pink para Oferta, brown para Cargo. */
    private fun colorFor(nombre: String, sunday: Boolean): Color {
        val index = when {
            nombre.contains("Público General") -> 0
            nombre.contains("Escolar General") -> 1
            nombre.contains("Adulto Mayor") -> 2
            nombre.contains("Int. hasta 15") -> 3
            nombre.contains("Int. hasta 50") -> 4
            nombre.contains("Escolar Intermedio") -> 5
            else -> -1
        }
        return when {
            index >= 0 -> palette.fare(index, sunday).background.copy(alpha = 1f)
            nombre.contains("Oferta") -> Color(0xFFEC407A)
            nombre.contains("Cargo") -> Color(0xFF8D6E63)
            else -> Color(0xFF4F8FC0)
        }
    }

    class Factory(private val c: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ReportViewModel(c) as T
    }
}
