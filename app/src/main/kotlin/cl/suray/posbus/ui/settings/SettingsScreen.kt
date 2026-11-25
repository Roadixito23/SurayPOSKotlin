package cl.suray.posbus.ui.settings

import cl.suray.posbus.ui.components.rememberAutoFocus
import cl.suray.posbus.ui.components.surayFieldColors
import cl.suray.posbus.ui.components.SurayFieldShape
import cl.suray.posbus.ui.components.SurayDialogCard
import cl.suray.posbus.ui.components.NumberInputDialog
import cl.suray.posbus.ui.components.ChangePasswordDialog
import cl.suray.posbus.ui.components.PasswordPromptDialog
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.RadioButton
import androidx.compose.ui.text.input.KeyboardCapitalization
import cl.suray.posbus.data.prefs.Ruta
import cl.suray.posbus.domain.Routes as DomainRoutes
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
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.TopAppBarDefaults
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
import cl.suray.posbus.domain.FareKind
import cl.suray.posbus.domain.FareTitle
import cl.suray.posbus.domain.FareTitles
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import cl.suray.posbus.R
import cl.suray.posbus.ui.components.HeaderBrush
import cl.suray.posbus.ui.components.SectionCard
import cl.suray.posbus.ui.theme.Hemiheads
import cl.suray.posbus.ui.theme.SurayColors
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

private val Clp: NumberFormat = NumberFormat.getIntegerInstance(Locale("es", "CL"))
private enum class SettingsTab(val title: String, val icon: Int?) {
    Terminal("Terminal", R.drawable.ic_receipt),
    Security("Seguridad", null),
    Payments("Pagos", R.drawable.ic_card),
    Printing("Impresión", R.drawable.ic_print),
    Prices("Precios", R.drawable.ic_money),
    Routes("Rutas", null),
    Appearance("Apariencia", null),
    Abbreviations("Abreviaturas", null),
}

@Composable
private fun SettingsTab.painter() = when (this) {
    SettingsTab.Security -> rememberVectorPainter(Icons.Default.Lock)
    SettingsTab.Appearance -> rememberVectorPainter(Icons.Default.Create)
    SettingsTab.Abbreviations -> rememberVectorPainter(Icons.Default.List)
    SettingsTab.Routes -> rememberVectorPainter(Icons.Default.LocationOn)
    else -> painterResource(icon!!)
}

/** Ajustes protegidos por la clave de Ajustes (`654321` por defecto). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, printerVm: PrinterDiagViewModel, onBack: () -> Unit, onButtonColors: () -> Unit = {}) {
    var unlocked by remember { mutableStateOf(false) }
    if (!unlocked) {
        PasswordPromptDialog(
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
    var tab by remember { mutableStateOf(SettingsTab.Terminal) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); message = null }
    }
    fun report(r: Rejection, ok: String) { message = r ?: ok }

    Scaffold(
        containerColor = SurayColors.Background,
        topBar = {
            Column(Modifier.shadow(4.dp)) {
                TopAppBar(
                    title = { Text("Configuración", fontFamily = Hemiheads, letterSpacing = 0.5.sp) },
                    navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent, titleContentColor = Color.White, navigationIconContentColor = Color.White,
                    ),
                    modifier = Modifier.background(HeaderBrush),
                )
                ScrollableTabRow(
                    selectedTabIndex = tab.ordinal, edgePadding = 8.dp,
                    containerColor = Color.White, contentColor = SurayColors.PrimaryDark,
                    indicator = { positions ->
                        TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[tab.ordinal]), height = 3.dp, color = SurayColors.Primary)
                    },
                    divider = {},
                ) {
                    SettingsTab.entries.forEach { t ->
                        Tab(
                            tab == t, { tab = t },
                            selectedContentColor = SurayColors.PrimaryDark, unselectedContentColor = Color(0xFF8A9BAB),
                            icon = { Icon(t.painter(), null, Modifier.size(22.dp)) },
                            text = { Text(t.title, fontSize = 12.sp, fontWeight = if (tab == t) FontWeight.Bold else FontWeight.Medium) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (tab) {
                SettingsTab.Terminal -> TerminalTab(state, vm, ::report)
                SettingsTab.Security -> SecurityTab(state, vm, ::report)
                SettingsTab.Payments -> PaymentsTab(state, vm, ::report)
                SettingsTab.Printing -> PrinterDiagTab(printerVm)
                SettingsTab.Prices -> PricesTab(state, vm, ::report)
                SettingsTab.Routes -> RoutesTab(state, vm, ::report)
                SettingsTab.Appearance -> AppearanceTab(state, vm, ::report, onButtonColors)
                SettingsTab.Abbreviations -> AbbreviationsTab(state, vm)
            }
        }
    }
}

@Composable
private fun Section(title: String, icon: Painter, subtitle: String? = null, content: @Composable () -> Unit) =
    SectionCard(title, icon, subtitle = subtitle, content = content)

@Composable
private fun TerminalTab(state: SettingsUiState, vm: SettingsViewModel, report: (Rejection, String) -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    var askReset by remember { mutableStateOf(false) }
    var editTerminal by remember { mutableStateOf(false) }

    Section("ID de terminal", painterResource(R.drawable.ic_bus), "Identificador de este dispositivo (1-99)") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Terminal ${state.terminalId}", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Button({ editTerminal = true }) { Text("Cambiar") }
        }
    }
    if (editTerminal) NumberInputDialog(
        title = "ID de terminal",
        label = "ID (1-99)",
        initial = state.terminalId.toString(),
        maxLength = 2,
        icon = painterResource(R.drawable.ic_bus),
        onSave = { text ->
            vm.saveTerminalId(text).also { if (it == null) { editTerminal = false; report(null, "ID guardado: ${text.trim()}") } }
        },
        onCancel = { editTerminal = false },
    )

    Section("N° Comprobante", painterResource(R.drawable.ic_receipt), "Último usado: ${state.comprobanteNumber}") {
        Text("Reinicia el contador de comprobantes. Requiere la clave del comprobante.", fontSize = 14.sp)
        Button({ askReset = true }) { Text("Reiniciar contador") }
    }

    if (askReset) PasswordPromptDialog(
        title = "Clave del comprobante",
        verify = vm::verifyComprobantePassword,
        onSuccess = { askReset = false; confirmReset = true },
        onCancel = { askReset = false },
    )
    if (confirmReset) SurayDialogCard(
        title = "Confirmar reinicio",
        onDismiss = { confirmReset = false },
        confirmText = "Reiniciar",
        onConfirm = { vm.resetComprobanteCounter(); confirmReset = false; report(null, "Contador reiniciado") },
        icon = rememberVectorPainter(Icons.Default.Warning),
        withKeyboard = false,
    ) { Text("¿Reiniciar el contador de comprobantes?", fontSize = 16.sp, textAlign = TextAlign.Center) }
}

@Composable
private fun SecurityTab(state: SettingsUiState, vm: SettingsViewModel, report: (Rejection, String) -> Unit) {
    var passwordDialog by remember { mutableStateOf<PasswordKind?>(null) }

    Section("Claves de acceso", rememberVectorPainter(Icons.Default.Lock), "Se guardan en el equipo") {
        OutlinedButton({ passwordDialog = PasswordKind.App }, Modifier.fillMaxWidth()) { Text("Cambiar clave de la app") }
        OutlinedButton({ passwordDialog = PasswordKind.Settings }, Modifier.fillMaxWidth()) { Text("Cambiar clave de Ajustes") }
        OutlinedButton({ passwordDialog = PasswordKind.Comprobante }, Modifier.fillMaxWidth()) { Text("Cambiar clave del comprobante") }
    }

    Section("Tarjetas NFC vinculadas", rememberVectorPainter(Icons.Default.Person), "Acceso rápido con tarjeta") {
        if (state.linkedCards.isEmpty()) Text("No hay tarjetas vinculadas", fontSize = 14.sp)
        state.linkedCards.forEach { id ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(id, Modifier.weight(1f))
                IconButton({ vm.removeNfcCard(id) }) { Icon(Icons.Default.Delete, "Quitar", tint = SurayColors.Red700) }
            }
        }
        Text("Vincular tarjetas nuevas estará disponible con la integración NFC.", fontSize = 12.sp, color = Color(0xFF6B7C8C))
    }

    passwordDialog?.let { kind ->
        ChangePasswordDialog(
            label = kind.label,
            verifyCurrent = when (kind) {
                PasswordKind.App -> vm::verifyAppPassword
                PasswordKind.Settings -> vm::verifySettingsPassword
                PasswordKind.Comprobante -> vm::verifyComprobantePassword
            },
            exactLength = kind == PasswordKind.Comprobante,
            onSave = { new ->
                when (kind) {
                    PasswordKind.App -> vm.changeAppPassword(new)
                    PasswordKind.Settings -> vm.changeSettingsPassword(new)
                    PasswordKind.Comprobante -> vm.changeComprobantePassword(new)
                }.also { if (it == null) { passwordDialog = null; report(null, "Clave actualizada") } }
            },
            onCancel = { passwordDialog = null },
        )
    }
}

@Composable
private fun PaymentsTab(state: SettingsUiState, vm: SettingsViewModel, report: (Rejection, String) -> Unit) {
    var affiliate by remember { mutableStateOf("") }

    Section("Pago con tarjeta (SumUp)", painterResource(R.drawable.ic_card), if (state.hasAffiliateKey) "Clave de afiliado configurada" else "Sin clave de afiliado") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Habilitar pago con tarjeta", Modifier.weight(1f))
            Switch(state.sumUpEnabled, vm::setSumUpEnabled)
        }
        if (!state.hasAffiliateKey) Text("Sin clave, el botón Tarjeta no se mostrará.", fontSize = 12.sp, color = Color(0xFF6B7C8C))
        OutlinedTextField(
            affiliate, { affiliate = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp),
            label = { Text(if (state.hasAffiliateKey) "Nueva clave de afiliado" else "Clave de afiliado") },
            visualTransformation = PasswordVisualTransformation(),
        )
        Button({
            val r = vm.saveAffiliateKey(affiliate)
            if (r == null) affiliate = ""
            report(r, "Clave de afiliado guardada")
        }) { Text("Guardar clave") }
    }
}

private enum class PasswordKind(val label: String) {
    App("clave de la app"), Settings("clave de Ajustes"), Comprobante("clave del comprobante")
}

@Composable
private fun PricesTab(state: SettingsUiState, vm: SettingsViewModel, report: (Rejection, String) -> Unit) {
    var editing by remember { mutableStateOf<Triple<Boolean, Int, Tarifa>?>(null) }
    var restore by remember { mutableStateOf<Boolean?>(null) }

    var editingTitle by remember { mutableStateOf<FareKind?>(null) }

    @Composable
    fun fares(kind: FareKind, title: FareTitle, sunday: Boolean, list: List<Tarifa>) = Section(
        title.name,
        painterResource(R.drawable.ic_money),
        // La abreviatura de la tarifa única no se ve en Inicio (no hay interruptor).
        subtitle = if (kind == FareKind.UNIFIED) "Nombre impreso en el ticket" else "Nombre impreso en el ticket · Inicio: ${title.short}",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Nombre de la tarifa", Modifier.weight(1f), fontSize = 14.sp, color = Color(0xFF6B7C8C))
            IconButton({ editingTitle = kind }) { Icon(Icons.Default.Edit, "Editar nombre de la tarifa", tint = SurayColors.Amber800) }
        }
        HorizontalDivider()
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
    Section("Tarifa única toda la semana", painterResource(R.drawable.ic_money), "Se cobran los precios de lunes a sábado todos los días") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.unifiedFare) "Activada" else "Desactivada", Modifier.weight(1f))
            Switch(state.unifiedFare, vm::setUnifiedFare)
        }
        if (!state.unifiedFare) Text("Se usan precios distintos los domingos y feriados, con detección automática.", fontSize = 12.sp, color = Color(0xFF6B7C8C))
    }
    // Con tarifa única solo hay una lista; los precios de domingo se conservan y reaparecen al desactivarla.
    if (state.unifiedFare) fares(FareKind.UNIFIED, state.unifiedTitle, false, state.weekdayFares)
    else fares(FareKind.WEEKDAY, state.weekdayTitle, false, state.weekdayFares)
    if (!state.unifiedFare) fares(FareKind.SUNDAY, state.sundayTitle, true, state.sundayFares)

    editingTitle?.let { kind ->
        val current = when (kind) {
            FareKind.UNIFIED -> state.unifiedTitle
            FareKind.WEEKDAY -> state.weekdayTitle
            FareKind.SUNDAY -> state.sundayTitle
        }
        FareTitleDialog(
            kind = kind,
            current = current,
            onSave = { name, short ->
                val r = vm.saveFareTitle(kind, name, short)
                if (r == null) { editingTitle = null; report(null, "Nombre de tarifa guardado") }
                r
            },
            onReset = { vm.resetFareTitle(kind); editingTitle = null; report(null, "Nombre de fábrica restaurado") },
            onCancel = { editingTitle = null },
        )
    }

    editing?.let { (sunday, index, t) ->
        var name by remember { mutableStateOf(t.nombre) }
        var price by remember { mutableStateOf(t.precio.toLong().toString()) }
        var error by remember { mutableStateOf<String?>(null) }
        val focus = rememberAutoFocus()
        fun save() {
            error = vm.saveFare(sunday, index, name, price)
            if (error == null) { editing = null; report(null, "Tarifa guardada") }
        }
        SurayDialogCard(
            title = "Editar tarifa",
            onDismiss = { editing = null },
            confirmText = "Guardar",
            onConfirm = ::save,
            icon = painterResource(R.drawable.ic_money),
            error = error,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name, { name = it; error = null }, Modifier.fillMaxWidth().focusRequester(focus),
                    label = { Text("Nombre") }, singleLine = true, shape = SurayFieldShape, colors = surayFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    price, { price = it.filter(Char::isDigit); error = null }, Modifier.fillMaxWidth(),
                    label = { Text("Precio (CLP)") }, singleLine = true, shape = SurayFieldShape, colors = surayFieldColors(),
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                )
            }
        }
    }
    restore?.let { sunday ->
        AlertDialog(
            onDismissRequest = { restore = null },
            title = { Text("Restaurar tarifas") },
            text = {
                val label = when {
                    sunday -> state.sundayTitle.name
                    state.unifiedFare -> state.unifiedTitle.name
                    else -> state.weekdayTitle.name
                }
                Text("Se perderán los nombres y precios personalizados de «$label».")
            },
            confirmButton = { TextButton({ vm.restoreDefaultFares(sunday); restore = null; report(null, "Tarifas restauradas") }) { Text("Restaurar") } },
            dismissButton = { TextButton({ restore = null }) { Text("Cancelar") } },
        )
    }
}

/**
 * Editor del nombre de una tarifa: el nombre se imprime en el ticket tal cual; la abreviatura titula el interruptor de
 * Inicio (con tarifa única no hay interruptor, así que se oculta y se conserva la de fábrica). Valida en vivo.
 */
@Composable
private fun FareTitleDialog(
    kind: FareKind,
    current: FareTitle,
    onSave: (name: String, short: String) -> Rejection,
    onReset: () -> Unit,
    onCancel: () -> Unit,
) {
    val showShort = kind != FareKind.UNIFIED
    var name by remember { mutableStateOf(current.name) }
    var short by remember { mutableStateOf(current.short) }
    var rejection by remember { mutableStateOf<String?>(null) }
    val focus = rememberAutoFocus()
    val validation = FareTitles.validate(name, short)
    val liveError = (validation as? FareTitles.Result.Invalid)?.reason
    fun save() {
        if (validation is FareTitles.Result.Ok) rejection = onSave(name, short)
    }

    SurayDialogCard(
        title = "Nombre de la tarifa",
        onDismiss = onCancel,
        confirmText = "Guardar",
        onConfirm = ::save,
        icon = painterResource(R.drawable.ic_money),
        confirmEnabled = validation is FareTitles.Result.Ok,
        error = rejection ?: liveError,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                name, { name = it.take(FareTitles.MAX_NAME + 4); rejection = null }, Modifier.fillMaxWidth().focusRequester(focus),
                label = { Text("Nombre (se imprime en el ticket tal cual)") },
                supportingText = { Text("${name.trim().length}/${FareTitles.MAX_NAME}") },
                isError = name.trim().isEmpty() || name.trim().length > FareTitles.MAX_NAME,
                singleLine = true, shape = SurayFieldShape, colors = surayFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = if (showShort) ImeAction.Next else ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )
            if (showShort) OutlinedTextField(
                short, { short = it.take(FareTitles.MAX_SHORT + 2); rejection = null }, Modifier.fillMaxWidth(),
                label = { Text("Abreviatura (interruptor de Inicio)") },
                supportingText = { Text("${short.trim().length}/${FareTitles.MAX_SHORT}") },
                isError = short.trim().isEmpty() || short.trim().length > FareTitles.MAX_SHORT,
                singleLine = true, shape = SurayFieldShape, colors = surayFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )
            TextButton(onReset, Modifier.fillMaxWidth()) {
                Text("Restaurar nombre de fábrica (${kind.defaultTitle.name})", color = SurayColors.Amber900, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun RoutesTab(state: SettingsUiState, vm: SettingsViewModel, report: (Rejection, String) -> Unit) {
    // Pair(índice o null si es nueva, ruta inicial)
    var editing by remember { mutableStateOf<Pair<Int?, Ruta>?>(null) }
    var deleting by remember { mutableStateOf<Int?>(null) }
    val pin = rememberVectorPainter(Icons.Default.LocationOn)

    Section("Rutas", pin, "El botón Destino del inicio alterna entre origen y destino de la ruta activa") {
        state.rutas.forEachIndexed { i, r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(i == state.rutaActiva, { vm.setActiveRoute(i); report(null, "Ruta activa: ${r.origen} - ${r.destino}") })
                Text("${r.origen} ⇄ ${r.destino}", Modifier.weight(1f), fontWeight = if (i == state.rutaActiva) FontWeight.Bold else FontWeight.Normal)
                IconButton({ editing = i to r }) { Icon(Icons.Default.Edit, "Editar") }
                IconButton({ deleting = i }, enabled = state.rutas.size > 1) { Icon(Icons.Default.Delete, "Borrar") }
            }
            if (i < state.rutas.lastIndex) HorizontalDivider()
        }
        OutlinedButton({ editing = null to Ruta("", "") }, Modifier.fillMaxWidth()) { Text("Agregar ruta") }
        Text(
            "Máximo ${DomainRoutes.NAME_MAX} caracteres por nombre, para que quepan en el botón y en los tickets.",
            fontSize = 12.sp, color = Color(0xFF6B7C8C),
        )
    }

    editing?.let { (index, r) ->
        var origen by remember { mutableStateOf(r.origen) }
        var destino by remember { mutableStateOf(r.destino) }
        var error by remember { mutableStateOf<String?>(null) }
        val focus = rememberAutoFocus()
        fun save() {
            error = vm.saveRoute(index, origen, destino)
            if (error == null) { editing = null; report(null, "Ruta guardada") }
        }
        @Composable
        fun field(value: String, label: String, onChange: (String) -> Unit, mod: Modifier, last: Boolean) = OutlinedTextField(
            value, { onChange(it.take(DomainRoutes.NAME_MAX)); error = null }, mod.fillMaxWidth(),
            label = { Text(label) }, singleLine = true, shape = SurayFieldShape, colors = surayFieldColors(),
            supportingText = { Text("${value.length}/${DomainRoutes.NAME_MAX}") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = if (last) ImeAction.Done else ImeAction.Next),
            keyboardActions = KeyboardActions(onDone = { save() }),
        )
        SurayDialogCard(
            title = if (index == null) "Nueva ruta" else "Editar ruta",
            onDismiss = { editing = null },
            confirmText = "Guardar",
            onConfirm = ::save,
            icon = pin,
            error = error,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                field(origen, "Origen", { origen = it }, Modifier.focusRequester(focus), false)
                field(destino, "Destino", { destino = it }, Modifier, true)
            }
        }
    }
    deleting?.let { i ->
        val r = state.rutas.getOrNull(i)
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Borrar ruta") },
            text = { Text("¿Borrar la ruta ${r?.origen} - ${r?.destino}?") },
            confirmButton = { TextButton({ deleting = null; report(vm.deleteRoute(i), "Ruta borrada") }) { Text("Borrar") } },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun AppearanceTab(state: SettingsUiState, vm: SettingsViewModel, report: (Rejection, String) -> Unit, onButtonColors: () -> Unit) {
    var showIcons by remember(state.showIcons) { mutableStateOf(state.showIcons) }
    var opacity by remember(state.buttonOpacity) { mutableStateOf(state.buttonOpacity) }
    var textSize by remember(state.textSize) { mutableStateOf(state.textSize) }
    var margin by remember(state.pdfMargin) { mutableStateOf(state.pdfMargin.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }) }

    Section("Botones de venta", rememberVectorPainter(Icons.Default.Create), "Íconos, opacidad y tamaño del texto") {
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

    Section("Colores de los botones", rememberVectorPainter(Icons.Default.Star)) {
        Text("Fondo, borde y opacidad de cada botón de venta")
        OutlinedButton(onButtonColors, Modifier.fillMaxWidth()) { Text("Personalizar colores") }
    }

    Section("Margen final del PDF", painterResource(R.drawable.ic_print), "Espacio en blanco al final del reporte impreso") {
        Text("En puntos (69 por defecto)", fontSize = 12.sp, color = Color(0xFF6B7C8C))
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
    Section("Abreviaturas para el PDF", painterResource(R.drawable.ic_receipt), "Nombre corto de cada tarifa en el cierre de caja") {
        edited.forEach { (name, abbr) ->
            OutlinedTextField(
                abbr, { edited = edited + (name to it) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(name) },
            )
        }
        Spacer(Modifier.height(4.dp))
        Button({ vm.saveAbbreviations(edited) }) { Text("Guardar") }
    }
}
