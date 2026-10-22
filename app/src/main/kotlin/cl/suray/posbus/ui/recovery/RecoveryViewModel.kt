package cl.suray.posbus.ui.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cl.suray.posbus.AppContainer
import cl.suray.posbus.domain.PdfFileInfo
import cl.suray.posbus.domain.RecoveryStats
import cl.suray.posbus.domain.ReportFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RecoveryUiState(
    val loading: Boolean = true,
    val query: String = "",
    val files: List<PdfFileInfo> = emptyList(),
    val stats: RecoveryStats = RecoveryStats(0, 0, "0.00"),
    val printing: Boolean = false,
) {
    val visible: List<PdfFileInfo> get() = ReportFiles.filter(files, query)
}

class RecoveryViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(RecoveryUiState())
    val state: StateFlow<RecoveryUiState> = _state

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val files = withContext(Dispatchers.IO) {
                c.pdfDir.listFiles { f -> f.isFile && f.name.endsWith(".pdf") }.orEmpty()
                    .map { PdfFileInfo(it.name, it.lastModified(), it.length()) }
            }
            _state.update {
                it.copy(loading = false, files = ReportFiles.sorted(files), stats = ReportFiles.stats(files, c.clock.nowMillis()))
            }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    fun print(file: PdfFileInfo) {
        if (_state.value.printing) return
        viewModelScope.launch {
            _state.update { it.copy(printing = true) }
            runCatching { c.pdfPrinter.printPdf(java.io.File(c.pdfDir, file.name), file.name.removeSuffix(".pdf")) }
                .onSuccess { _messages.tryEmit("Documento enviado a impresora") }
                .onFailure { _messages.tryEmit("Error al imprimir el PDF: ${it.message}") }
            _state.update { it.copy(printing = false) }
        }
    }

    class Factory(private val c: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = RecoveryViewModel(c) as T
    }
}
