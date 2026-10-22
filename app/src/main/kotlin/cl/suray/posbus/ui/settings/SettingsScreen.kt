package cl.suray.posbus.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cl.suray.posbus.data.prefs.Tarifa
import java.text.NumberFormat
import java.util.Locale

private val Clp: NumberFormat = NumberFormat.getIntegerInstance(Locale("es", "CL"))
private val TABS = listOf("General", "Precios", "Apariencia", "Abreviaturas")

/** Ajustes protegidos por la clave de Ajustes (`654321` por defecto). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, onButtonColors: () -> Unit = {}) {
    var unlocked by remember { mutableStateOf(false) }
    if (!unlocked) {
        PasswordDialog(
            title = "Configuración",
            verify = vm::verifySettingsPassword,
            onSuccess = { unlocked = true },
            onCancel = onBack,
        )
        return
    }

    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var message by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(0) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); message = null }
    }
    fun report(r: Rejection, ok: String) { message = r ?: ok }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                TABS.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (tab) {
                    0 -> GeneralTab(state, vm, ::report)
                    1 -> PricesTab(state, vm, ::report)
                    2 -> AppearanceTab(state, vm, ::report, onButtonColors)
                    else -> AbbreviationsTab(state, vm)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun GeneralTab(state: SettingsUiState, vm: SettingsViewModel, report: (Rejection, String) -> Unit) {
    var terminal by remember(state.terminalId) { mutableStateOf(state.terminalId.toString()) }
    var affiliate by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }
    var askReset by remember { mutableStateOf(false) }
    var passwordDialog by remember { mutableStateOf<PasswordKind?>(null) }

    Section("ID de terminal") {
        Text("Identificador de este dispositivo (1-99)")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                terminal, { terminal = it.filter(Char::isDigit).take(2) },
                Modifier.width(120.dp), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.width(12.dp))
            Button({ report(vm.saveTerminalId(terminal), "ID guardado: $terminal") }) { Text("Guardar") }
        }
    }

    Section("N° Comprobante") {
        Text("Último usado: ${state.comprobanteNumber}. Reinicia el contador (requiere la clave del comprobante).")
        Button({ askReset = true }) { Text("Reiniciar contador") }
    }

    Section("Claves") {
        OutlinedButton({ passwordDialog = PasswordKind.App }, Modifier.fillMaxWidth()) { Text("Cambiar clave de la app") }
        OutlinedButton({ passwordDialog = PasswordKind.Settings }, Modifier.fillMaxWidth()) { Text("Cambiar clave de Ajustes") }
        OutlinedButton({ passwordDialog = PasswordKind.Comprobante }, Modifier.fillMaxWidth()) { Text("Cambiar clave del comprobante") }
    }

    Section("Pago con tarjeta (SumUp)") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Habilitar pago con tarjeta", Modifier.weight(1f))
            Switch(state.sumUpEnabled, vm::setSumUpEnabled)
        }
        Text(if (state.hasAffiliateKey) "Clave de afiliado configurada" else "Sin clave de afiliado: el botón Tarjeta no se mostrará")
        OutlinedTextField(
            affiliate, { affiliate = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(if (state.hasAffiliateKey) "Nueva clave de afiliado" else "Clave de afiliado") },
            visualTransformation = PasswordVisualTransformation(),
        )
        Button({
            val r = vm.saveAffiliateKey(affiliate)
            if (r == null) affiliate = ""
            report(r, "Clave de afiliado guardada")
        }) { Text("Guardar clave") }
    }

    Section("Tarjetas NFC vinculadas") {
        if (state.linkedCards.isEmpty()) Text("No hay tarjetas vinculadas")
        state.linkedCards.forEach { id ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(id, Modifier.weight(1f))
                IconButton({ vm.removeNfcCard(id) }) { Icon(Icons.Default.Delete, "Quitar") }
            }
        }
        Text("Vincular tarjetas nuevas estará disponible con la integración NFC.")
    }

    if (askReset) PasswordDialog(
        title = "Clave del comprobante",
        verify = vm::verifyComprobantePassword,
        onSuccess = { askReset = false; confirmReset = true },
        onCancel = { askReset = false },
    )
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Confirmar reinicio") },
        text = { Text("¿Reiniciar el contador de comprobantes?") },
        confirmButton = {
            TextButton({ vm.resetComprobanteCounter(); confirmReset = false; report(null, "Contador reiniciado") }) { Text("Reiniciar") }
        },
        dismissButton = { TextButton({ confirmReset = false }) { Text("Cancelar") } },
    )
    passwordDialog?.let { kind ->
        ChangePasswordDialog(
            kind,
            onConfirm = { new ->
                val r = when (kind) {
                    PasswordKind.App -> vm.changeAppPassword(new)
                    PasswordKind.Settings -> vm.changeSettingsPassword(new)
                    PasswordKind.Comprobante -> vm.changeComprobantePassword(new)
                }
                if (r == null) passwordDialog = null
                report(r, "Clave actualizada")
            },
            onDismiss = { passwordDialog = null },
        )
    }
}

private enum class PasswordKind(val label: String) {
    App("clave de la app"), Settings("clave de Ajustes"), Comprobante("clave del comprobante")
}

@Composable
private fun PricesTab(state: SettingsUiState, vm: SettingsViewModel, report: (Rejection, String) -> Unit) {
    var editing by remember { mutableStateOf<Triple<Boolean, Int, Tarifa>?>(null) }
    var restore by remember { mutableStateOf<Boolean?>(null) }

    @Composable
    fun fares(title: String, sunday: Boolean, list: List<Tarifa>) = Section(title) {
        list.forEachIndexed { i, t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.nombre, Modifier.weight(1f))
                Text("$${Clp.format(t.precio.toLong())}", fontWeight = FontWeight.Bold)
                IconButton({ editing = Triple(sunday, i, t) }) { Icon(Icons.Default.Edit, "Editar") }
            }
            if (i < list.lastIndex) HorizontalDivider()
        }
        TextButton({ restore = sunday }) { Text("Restaurar valores por defecto") }
    }
    fares("Lunes a sábado", false, state.weekdayFares)
    fares("Domingo y feriados", true, state.sundayFares)

    editing?.let { (sunday, index, t) ->
        var name by remember { mutableStateOf(t.nombre) }
        var price by remember { mutableStateOf(t.precio.toLong().toString()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Editar tarifa") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true)
                    OutlinedTextField(
                        price, { price = it.filter(Char::isDigit) }, label = { Text("Precio (CLP)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                TextButton({
                    val r = vm.saveFare(sunday, index, name, price)
                    if (r == null) editing = null
                    report(r, "Tarifa guardada")
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton({ editing = null }) { Text("Cancelar") } },
        )
    }
    restore?.let { sunday ->
        AlertDialog(
            onDismissRequest = { restore = null },
            title = { Text("Restaurar tarifas") },
            text = { Text("Se perderán los nombres y precios personalizados de ${if (sunday) "domingo" else "lunes a sábado"}.") },
            confirmButton = { TextButton({ vm.restoreDefaultFares(sunday); restore = null; report(null, "Tarifas restauradas") }) { Text("Restaurar") } },
            dismissButton = { TextButton({ restore = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun AppearanceTab(state: SettingsUiState, vm: SettingsViewModel, report: (Rejection, String) -> Unit, onButtonColors: () -> Unit) {
    var showIcons by remember(state.showIcons) { mutableStateOf(state.showIcons) }
    var opacity by remember(state.buttonOpacity) { mutableStateOf(state.buttonOpacity) }
    var textSize by remember(state.textSize) { mutableStateOf(state.textSize) }
    var margin by remember(state.pdfMargin) { mutableStateOf(state.pdfMargin.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }) }

    Section("Botones") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mostrar íconos", Modifier.weight(1f))
            Switch(showIcons, { showIcons = it })
        }
        Text("Opacidad: ${"%.0f".format(opacity * 100)}%")
        Slider(opacity, { opacity = it }, valueRange = 0.3f..1f)
        Text("Tamaño del texto: ${"%.1f".format(textSize)}×")
        Slider(textSize, { textSize = it }, valueRange = 0.5f..1.5f)
        Button({ vm.saveAppearance(showIcons, opacity, textSize); report(null, "Preferencias guardadas") }) { Text("Guardar") }
    }

    Section("Colores de los botones") {
        Text("Fondo, borde y opacidad de cada botón de venta")
        OutlinedButton(onButtonColors, Modifier.fillMaxWidth()) { Text("Personalizar colores") }
    }

    Section("Margen final del PDF") {
        Text("Espacio en blanco al final del reporte impreso (puntos)")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                margin, { margin = it }, Modifier.width(140.dp), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Spacer(Modifier.width(12.dp))
            Button({ report(vm.savePdfMargin(margin), "Margen guardado") }) { Text("Guardar") }
        }
    }
}

@Composable
private fun AbbreviationsTab(state: SettingsUiState, vm: SettingsViewModel) {
    var edited by remember(state.abbreviations) { mutableStateOf(state.abbreviations) }
    Section("Abreviaturas para el PDF") {
        edited.forEach { (name, abbr) ->
            OutlinedTextField(
                abbr, { edited = edited + (name to it) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(name) },
            )
        }
        Spacer(Modifier.height(4.dp))
        Button({ vm.saveAbbreviations(edited) }) { Text("Guardar") }
    }
}

@Composable
private fun PasswordDialog(title: String, verify: (String) -> Boolean, onSuccess: () -> Unit, onCancel: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                input, { input = it.take(12); error = false }, singleLine = true, isError = error,
                label = { Text(if (error) "Clave incorrecta" else "Clave") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
        },
        confirmButton = { TextButton({ if (verify(input)) onSuccess() else { error = true; input = "" } }) { Text("Aceptar") } },
        dismissButton = { TextButton(onCancel) { Text("Cancelar") } },
    )
}

@Composable
private fun ChangePasswordDialog(kind: PasswordKind, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var new by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar ${kind.label}") },
        text = {
            OutlinedTextField(
                new, { new = it.take(12) }, singleLine = true, label = { Text("Nueva clave (6 dígitos)") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
        },
        confirmButton = { TextButton({ onConfirm(new) }) { Text("Guardar") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } },
    )
}
