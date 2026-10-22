package cl.suray.posbus.ui.backup

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import cl.suray.posbus.domain.PdfFileInfo
import cl.suray.posbus.domain.ReportFiles
import kotlin.system.exitProcess

/** "Respaldo y Recuperación": crear, compartir, exportar, importar y restaurar archivos `.suray`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(vm: BackupViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<PdfFileInfo?>(null) }
    var confirmRestore by remember { mutableStateOf<PdfFileInfo?>(null) }
    var exporting by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val file = exporting
        if (uri != null && file != null) vm.export(file, context.contentResolver, uri)
        exporting = null
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val displayName = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "importado.suray"
            vm.import(context.contentResolver, uri, displayName)
        }
    }

    fun share(file: PdfFileInfo) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", vm.file(file.name))
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Respaldo App Suray")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartir copia de seguridad"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Respaldo y Recuperación") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), label = { Text("Nombre de la copia (opcional)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        { vm.create(name) { created -> name = ""; selected = vm.state.value.backups.firstOrNull { it.name == created } ?: PdfFileInfo(created, 0, 0) } },
                        Modifier.weight(1f), enabled = !state.busy,
                    ) { Text("Crear copia") }
                    OutlinedButton({ importLauncher.launch(arrayOf("*/*")) }, Modifier.weight(1f), enabled = !state.busy) { Text("Importar archivo") }
                }
                Text("Copias disponibles", fontWeight = FontWeight.Bold)
                if (state.backups.isEmpty()) {
                    Text("No hay copias de seguridad disponibles", fontSize = 14.sp)
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.backups, key = { it.name }) { b ->
                        Card(Modifier.fillMaxWidth().clickable { selected = b }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(b.name, fontWeight = FontWeight.Medium)
                                Text("${ReportFiles.dateTime(b)} · ${ReportFiles.size(b.sizeBytes)}", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            if (state.busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }

    selected?.let { b ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Copia de seguridad") },
            text = {
                Column {
                    Text(b.name, fontWeight = FontWeight.Bold)
                    if (b.sizeBytes > 0) Text("${ReportFiles.dateTime(b)} · ${ReportFiles.size(b.sizeBytes)}")
                    Text("La clave de afiliado de SumUp no se incluye en las copias.", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton({ selected = null; share(b) }) { Text("Compartir") }
                    TextButton({ selected = null; exporting = b.name; exportLauncher.launch(b.name) }) { Text("Exportar a un archivo") }
                    TextButton({ selected = null; confirmRestore = b }) { Text("Restaurar") }
                }
            },
            dismissButton = { TextButton({ selected = null }) { Text("Cerrar") } },
        )
    }

    confirmRestore?.let { b ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Restaurar copia de seguridad") },
            text = { Text("Esto reemplazará los datos actuales (preferencias, reportes y, si la copia los trae, los cierres guardados). La aplicación se cerrará al terminar.") },
            confirmButton = { TextButton({ confirmRestore = null; vm.restore(b.name) }) { Text("Restaurar") } },
            dismissButton = { TextButton({ confirmRestore = null }) { Text("Cancelar") } },
        )
    }

    if (state.restartRequired) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Restauración completada") },
            text = { Text("Cierre la aplicación y ábrala de nuevo para aplicar todos los cambios.") },
            confirmButton = { TextButton({ exitProcess(0) }) { Text("Cerrar la aplicación") } },
        )
    }
}
