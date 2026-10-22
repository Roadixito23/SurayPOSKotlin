package cl.suray.posbus.ui.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cl.suray.posbus.AppContainer
import cl.suray.posbus.data.backup.BackupException
import cl.suray.posbus.domain.PdfFileInfo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BackupUiState(
    val busy: Boolean = false,
    val backups: List<PdfFileInfo> = emptyList(),
    /** Tras restaurar la base de datos la app debe reiniciarse. */
    val restartRequired: Boolean = false,
)

class BackupViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init { reload() }

    fun file(name: String) = File(File(c.appFilesDir, "suray_backups"), name)

    fun reload() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { c.backup.list().map { PdfFileInfo(it.name, it.lastModified(), it.length()) } }
            _state.update { it.copy(backups = list) }
        }
    }

    /** Devuelve el archivo creado (para ofrecer exportarlo) o `null` si falló. */
    fun create(name: String, onCreated: (String) -> Unit) = run("Creando copia de seguridad…") {
        val f = c.backup.create(name.ifBlank { "backup" })
        _messages.tryEmit("Copia de seguridad creada")
        onCreated(f.name)
    }

    fun export(name: String, resolver: ContentResolver, target: Uri) = run("Exportando…") {
        resolver.openOutputStream(target)?.use { out -> file(name).inputStream().use { it.copyTo(out) } }
            ?: throw BackupException("No se pudo abrir el destino")
        _messages.tryEmit("Copia exportada")
    }

    fun import(resolver: ContentResolver, source: Uri, displayName: String) = run("Importando…") {
        val input = resolver.openInputStream(source) ?: throw BackupException("No se pudo abrir el archivo")
        c.backup.import(input, displayName)
        _messages.tryEmit("Copia importada")
    }

    fun restore(name: String) = run("Restaurando…") {
        val s = c.backup.restore(file(name))
        _state.update { it.copy(restartRequired = true) }
        _messages.tryEmit("Restaurado: ${s.preferences} preferencias, ${s.pdfs} reportes" + if (s.databaseRestored) ", historial de cierres" else "")
    }

    private fun run(label: String, block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching { withContext(Dispatchers.IO) { block() } }
                .onFailure { _messages.tryEmit(if (it is BackupException) it.message ?: label else "Error: ${it.message}") }
            _state.update { it.copy(busy = false) }
            reload()
        }
    }

    class Factory(private val c: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BackupViewModel(c) as T
    }
}
