package cl.suray.posbus.ui.report

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.suray.posbus.R
import cl.suray.posbus.ui.theme.Hemiheads
import cl.suray.posbus.ui.theme.SurayColors
import java.text.NumberFormat
import java.util.Locale

private val Clp: NumberFormat = NumberFormat.getIntegerInstance(Locale("es", "ES"))
private val Turquoise = SurayColors.Primary
private val TurquoiseDark = SurayColors.PrimaryDark
private val Coral = SurayColors.Error

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(vm: ReportViewModel, onBack: () -> Unit, onOldReports: () -> Unit) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it.text) } }

    Scaffold(
        containerColor = SurayColors.Background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Reporte de Caja", fontFamily = Hemiheads, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Volver") } },
                actions = { Text("Anteriores", Modifier.clickable(onClick = onOldReports).padding(12.dp), color = Color.White, fontSize = 13.sp) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Turquoise, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White, actionIconContentColor = Color.White,
                ),
            )
        },
        bottomBar = { BottomBar(state.isEmpty || state.closing, vm::closeRegister) },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            Column(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard(state)
                TransactionsCard(state, vm::toggleSort, Modifier.weight(1f))
            }
            if (state.closing) LoadingOverlay(state.statusMessage)
        }
    }
}

@Composable
private fun SummaryCard(state: ReportUiState) {
    Card(shape = RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(3.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${state.count}", fontFamily = Hemiheads, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TurquoiseDark)
                    Text("Transacciones", fontSize = 11.sp, color = Color(0xFF757575))
                }
                Box(Modifier.width(1.dp).height(44.dp).background(Color(0xFFEEEEEE)))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$${Clp.format(state.total)}", fontFamily = Hemiheads, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Coral, maxLines = 1)
                    Text("Total", fontSize = 11.sp, color = Color(0xFF757575))
                }
            }
            if (state.cardTotal > 0) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFFEEEEEE))
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$${Clp.format(state.cashTotal)}", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF388E3C))
                    Box(Modifier.width(1.dp).height(20.dp).background(Color(0xFFEEEEEE)))
                    Text("$${Clp.format(state.cardTotal)}", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                }
                Row {
                    Text("Efectivo", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 10.sp, color = Color(0xFF757575))
                    Text("Tarjeta", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 10.sp, color = Color(0xFF757575))
                }
            }
        }
    }
}

@Composable
private fun TransactionsCard(state: ReportUiState, onSort: () -> Unit, modifier: Modifier) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(3.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(ImageVector.vectorResource(R.drawable.ic_receipt), null, Modifier.size(17.dp), tint = TurquoiseDark)
                Spacer(Modifier.width(6.dp))
                Text("Transacciones", fontFamily = Hemiheads, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TurquoiseDark)
                Spacer(Modifier.weight(1f))
                if (!state.isEmpty) {
                    Row(Modifier.clickable(onClick = onSort).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp).rotate(if (state.newestFirst) 0f else 180f), tint = TurquoiseDark)
                        Text(if (state.newestFirst) "Más nuevo" else "Más antiguo", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TurquoiseDark)
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            if (state.isEmpty) {
                Box(Modifier.fillMaxWidth().padding(vertical = 22.dp), contentAlignment = Alignment.Center) {
                    Text("No hay transacciones registradas", fontSize = 13.sp, color = Color(0xFF757575), fontStyle = FontStyle.Italic)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.rows, key = { it.tx.id }) { TransactionRow(it) }
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(row: ReportRow) {
    val (bar, bg) = when (row.kind) {
        RowKind.PastDay -> Coral to Color(0xFFFFCDD2).copy(alpha = 0.15f)
        RowKind.Cancellation -> Color(0xFFEF5350) to Color(0xFFFFEBEE)
        RowKind.Normal -> row.color to row.color.copy(alpha = 0.08f)
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(9.dp)).background(bg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(46.dp).background(bar))
        Spacer(Modifier.width(10.dp))
        when (row.kind) {
            RowKind.PastDay -> Icon(Icons.Default.Warning, null, Modifier.size(20.dp), tint = bar)
            else -> Icon(ImageVector.vectorResource(R.drawable.ic_receipt), null, Modifier.size(20.dp), tint = bar)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(vertical = 7.dp)) {
            Text(row.tx.nombre, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
            Text("${row.tx.dia}/${row.tx.mes} ${row.tx.hora}  ·  ${row.tx.comprobante}", fontSize = 11.sp, color = Color(0xFF757575))
        }
        Text("$${Clp.format(row.tx.valor)}", Modifier.padding(end = 10.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (row.tx.valor < 0) Color(0xFFD32F2F) else Color(0xFF212121))
    }
}

@Composable
private fun BottomBar(disabled: Boolean, onClose: () -> Unit) {
    Box(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp)) {
        Button(
            onClick = onClose,
            enabled = !disabled,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Turquoise, disabledContainerColor = Color(0xFFBDBDBD), contentColor = Color.White, disabledContentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(46.dp),
        ) {
            Icon(ImageVector.vectorResource(R.drawable.ic_print), null, Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text("Cerrar Caja e Imprimir", fontFamily = Hemiheads, fontSize = 15.sp)
        }
    }
}

@Composable
private fun LoadingOverlay(message: String) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.54f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Turquoise)
                Spacer(Modifier.height(16.dp))
                Text(message, fontFamily = Hemiheads, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}
