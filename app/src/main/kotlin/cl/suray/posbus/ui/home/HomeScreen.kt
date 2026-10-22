package cl.suray.posbus.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.suray.posbus.R
import cl.suray.posbus.data.prefs.Tarifa
import cl.suray.posbus.domain.CargoCopy
import cl.suray.posbus.ui.theme.Hemiheads
import cl.suray.posbus.ui.theme.SurayColors
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch

enum class Destination(val label: String) {
    Reports("Cierres de Caja"),
    Statistics("Estadísticas"),
    Transfer("Transferencia"),
    Recovery("Recuperación de Reportes"),
    Backup("Copias de Seguridad"),
    Settings("Configuración"),
}

private val Clp: NumberFormat = NumberFormat.getIntegerInstance(Locale("es", "CL"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: HomeViewModel, onNavigate: (Destination) -> Unit, onCargo: () -> Unit, onLogout: () -> Unit) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var payment by remember { mutableStateOf<Tarifa?>(null) }
    var askCancel by remember { mutableStateOf(false) }
    var askReprint by remember { mutableStateOf(false) }
    var cargoCopies by remember { mutableStateOf(false) }
    var showOffer by remember { mutableStateOf(false) }
    var pendingAlert by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it.text, duration = SnackbarDuration.Short) }
    }
    LaunchedEffect(Unit) { vm.pendingAlert.collect { pendingAlert = it } }

    fun requestSale(t: Tarifa) {
        if (state.cardAvailable) payment = t else vm.sell(t.nombre, t.precio)
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(Modifier.width(300.dp)) {
                Text("Menú", Modifier.padding(20.dp), fontFamily = Hemiheads, fontSize = 22.sp, color = SurayColors.PrimaryDark)
                Destination.entries.forEach { d ->
                    NavigationDrawerItem(label = { Text(d.label) }, selected = false, onClick = {
                        scope.launch { drawer.close() }
                        onNavigate(d)
                    })
                }
                NavigationDrawerItem(label = { Text("Cerrar Sesión") }, selected = false, onClick = {
                    scope.launch { drawer.close() }
                    onLogout()
                })
            }
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = Color.Transparent,
        ) { inner ->
            Box(Modifier.fillMaxSize()) {
                Image(
                    painterResource(if (state.sundayRate) R.drawable.bg_rojo else R.drawable.bg_blanco),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Column(Modifier.fillMaxSize().padding(inner)) {
                    TopBar(
                        state = state,
                        onMenu = { scope.launch { drawer.open() } },
                        onCancel = { askCancel = true },
                        onReprint = { askReprint = true },
                    )
                    if (state.hasPending) PendingBanner(onClose = { onNavigate(Destination.Reports) })
                    StatusBand(state, onRate = vm::setSundayRate)
                    SalesGrid(state, vm, onSell = ::requestSale, onOffer = { showOffer = true }, onCargo = onCargo)
                }
            }
        }
    }

    payment?.let { t ->
        PaymentSheet(
            tarifa = t,
            onCash = { payment = null; vm.sell(t.nombre, t.precio) },
            onCard = { payment = null; vm.notifyCardPending() },
            onDismiss = { payment = null },
        )
    }
    if (askCancel) {
        PasswordDialog(
            title = "Anular última venta",
            verify = vm::verifyPassword,
            onConfirmed = { askCancel = false; vm.cancelLast() },
            onDismiss = { askCancel = false },
        )
    }
    if (askReprint) {
        PasswordDialog(
            title = "Reimprimir último ticket",
            verify = vm::verifyPassword,
            onConfirmed = { askReprint = false; if (state.lastIsCargo) cargoCopies = true else vm.reprintLast() },
            onDismiss = { askReprint = false },
        )
    }
    if (cargoCopies) {
        AlertDialog(
            onDismissRequest = { cargoCopies = false },
            title = { Text("Reimprimir cargo", fontWeight = FontWeight.Bold) },
            text = { Text("¿Qué copia desea reimprimir?") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton({ cargoCopies = false; vm.reprintLast(listOf(CargoCopy.Client)) }) { Text("Cliente") }
                    TextButton({ cargoCopies = false; vm.reprintLast(listOf(CargoCopy.Cargo)) }) { Text("Carga") }
                    TextButton({ cargoCopies = false; vm.reprintLast(listOf(CargoCopy.Client, CargoCopy.Cargo)) }) { Text("Ambas") }
                }
            },
            dismissButton = { TextButton({ cargoCopies = false }) { Text("Cancelar") } },
        )
    }
    if (showOffer) {
        OfferDialog(onSubmit = vm::sellOffer, onClose = { showOffer = false })
    }
    pendingAlert?.let { days ->
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Default.Warning, null, tint = Color(0xFFFF9800)) },
            title = { Text("Cierre de Caja Pendiente") },
            text = {
                Text(
                    "No es posible generar ventas porque existen transacciones de hace $days " +
                        "${if (days == 1) "día" else "días"} sin cerrar.\n\nDebe cerrar la caja para continuar usando el sistema.",
                )
            },
            dismissButton = { TextButton({ pendingAlert = null; vm.alertDismissed() }) { Text("Cancelar") } },
            confirmButton = {
                Button(
                    onClick = { pendingAlert = null; vm.alertDismissed(); onNavigate(Destination.Reports) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8F00)),
                ) { Text("Ir a Reportes") }
            },
        )
    }
}

@Composable
private fun TopBar(state: HomeUiState, onMenu: () -> Unit, onCancel: () -> Unit, onReprint: () -> Unit) {
    val colors = if (state.hasPending) listOf(SurayColors.Orange800, SurayColors.Red700)
    else listOf(SurayColors.Amber700, SurayColors.Amber900)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
            .background(Brush.linearGradient(colors))
            .statusBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleButton(SurayColors.Primary, true, onMenu) { Icon(Icons.Default.Menu, "Menú", tint = Color.White) }
            Image(painterResource(R.drawable.logo), null, Modifier.padding(start = 6.dp).height(30.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(SurayColors.Orange).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(ImageVector.vectorResource(R.drawable.ic_receipt), null, Modifier.size(20.dp), tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text(state.nextComprobante.toString().padStart(6, '0'), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 1.sp)
            }
            if (state.hasPending) {
                Spacer(Modifier.width(10.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(SurayColors.Red900).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Warning, null, Modifier.size(16.dp), tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text("${state.pendingDays}d", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.weight(1f))
            CircleButton(if (state.canCancel) SurayColors.Error else Color(0xFFE0E0E0), state.canCancel, onCancel) {
                Icon(Icons.Default.Delete, "Anular", tint = if (state.canCancel) Color.White else Color(0xFF757575))
            }
            Spacer(Modifier.width(6.dp))
            CircleButton(if (state.canReprint) SurayColors.Accent else Color(0xFFE0E0E0), state.canReprint, onReprint) {
                Icon(ImageVector.vectorResource(R.drawable.ic_print), "Reimprimir", tint = if (state.canReprint) Color.White else Color(0xFF757575))
            }
            Spacer(Modifier.width(6.dp))
        }
        Text(
            "${state.dayName}  ${state.dateText}",
            Modifier.fillMaxWidth().height(20.dp),
            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CircleButton(color: Color, enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(color).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun PendingBanner(onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFFFCDD2)).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Warning, null, Modifier.size(18.dp), tint = Color.Red)
        Spacer(Modifier.width(8.dp))
        Text("Hay un dia con ventas sin cerrar", Modifier.weight(1f), color = SurayColors.Red900, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text("Cerrar", Modifier.clickable(onClick = onClose).padding(4.dp), color = SurayColors.Red900, fontSize = 11.sp)
    }
}

@Composable
private fun StatusBand(state: HomeUiState, onRate: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 10.dp, vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TransactionCounter(state.salesToday, state.cancellationsToday)
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.autoRate) Text("⚡", fontSize = 12.sp, color = Color(0xFFFFC107), modifier = Modifier.padding(end = 3.dp))
            Text(
                if (state.sundayRate) "D/F" else "L-S",
                fontFamily = Hemiheads, fontSize = 15.sp,
                color = if (state.sundayRate) Color.Red else Color.White,
            )
            Switch(
                checked = state.sundayRate,
                onCheckedChange = onRate,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.Red, checkedTrackColor = Color.Red.copy(alpha = 0.5f)),
            )
        }
    }
}

@Composable
private fun TransactionCounter(sales: Int, cancellations: Int) {
    Row(Modifier.height(40.dp).width(120.dp).clip(RoundedCornerShape(10.dp))) {
        Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF1900A2)), contentAlignment = Alignment.Center) {
            Text("$sales", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFFFF0C00)), contentAlignment = Alignment.Center) {
            Text("$cancellations", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun SalesGrid(state: HomeUiState, vm: HomeViewModel, onSell: (Tarifa) -> Unit, onOffer: () -> Unit, onCargo: () -> Unit) {
    val fares = state.fares
    val enabled = !state.busy && !state.hasPending
    val sunday = state.sundayRate
    val defaultSchoolMid = Tarifa("Escolar Intermedio", if (sunday) 1300.0 else 1000.0)

    @Composable
    fun fare(i: Int, icon: @Composable () -> Unit, textColor: Color = Color.White, fallback: Tarifa? = null) {
        val t = fares.getOrNull(i) ?: fallback ?: return
        val c = state.colors.fare(i, sunday)
        SaleButton(t.nombre, icon, c, textColor, enabled, state.display) { onSell(t) }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.padding(start = 15.dp, end = 15.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    SectionHeader("PASAJEROS")
                    fare(0, { Icon(Icons.Default.Person, null) })
                    fare(1, { Icon(Icons.Default.Face, null) })
                    fare(2, { Icon(Icons.Default.AccountCircle, null) })
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    SectionHeader("INTERMEDIO")
                    fare(4, { Icon(Icons.Default.LocationOn, null) })
                    fare(3, { Icon(ImageVector.vectorResource(R.drawable.ic_bus), null) })
                    fare(5, { Icon(Icons.Default.Face, null) }, Color.Black, defaultSchoolMid)
                }
            }
            Spacer(Modifier.height(1.dp))
            SectionHeader("OTROS")
            Row(Modifier.height(60.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    SaleButton("O F E R T A", { Icon(Icons.Default.Star, null) }, state.colors.otros(0), Color.Black, enabled, state.display, onOffer)
                }
                Box(Modifier.weight(1f)) {
                    SaleButton("C A R G O", { Icon(Icons.Default.ShoppingCart, null) }, state.colors.otros(1), Color.Black, enabled, state.display, onCargo)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, Modifier.fillMaxWidth(), color = Color.Black, fontFamily = Hemiheads, fontSize = 13.sp, textAlign = TextAlign.Center,
        style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.White, blurRadius = 4f)))
}

@Composable
private fun SaleButton(
    text: String,
    icon: @Composable () -> Unit,
    colors: ButtonColors,
    textColor: Color,
    enabled: Boolean,
    display: DisplayPrefs,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(3.dp, if (enabled) colors.border else Color.Gray),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.background,
            contentColor = textColor,
            disabledContainerColor = Color.Gray,
            disabledContentColor = textColor.copy(alpha = 0.6f),
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxWidth().height(60.dp),
    ) {
        val size = (18f * display.textSizeMultiplier).sp
        if (display.showIcons) {
            Box(Modifier.size(size.value.dp * 0.9f + 4.dp)) { icon() }
        }
        Text(text, fontSize = size, lineHeight = size * 1.2f, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentSheet(tarifa: Tarifa, onCash: () -> Unit, onCard: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Método de Pago", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF424242))
            Spacer(Modifier.height(6.dp))
            Text("${tarifa.nombre} — $${Clp.format(tarifa.precio)}", fontSize = 14.sp, color = Color(0xFF757575))
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onCash, Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))) { Text("Efectivo", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                Button(onCard, Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))) { Text("Tarjeta", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun PasswordDialog(title: String, verify: (String) -> Boolean, onConfirmed: () -> Unit, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    fun check() { if (verify(input)) onConfirmed() else { error = true; input = "" } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { if (it.length <= 6) { input = it; error = false } },
                label = { Text("Contraseña") },
                supportingText = { Text(if (error) "Contraseña incorrecta" else "Máximo 6 dígitos") },
                isError = error,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
        },
        confirmButton = { Button(onClick = ::check) { Text("Confirmar") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } },
    )
}
