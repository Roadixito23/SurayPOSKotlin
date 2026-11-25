package cl.suray.posbus.ui.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.suray.posbus.data.prefs.OfferEntry
import cl.suray.posbus.domain.Offers
import cl.suray.posbus.domain.formatClp
import cl.suray.posbus.ui.components.SurayFieldShape
import cl.suray.posbus.ui.components.surayFieldColors
import cl.suray.posbus.ui.theme.SurayColors

/** Línea editable con id estable (para que Compose no mezcle el foco al quitar líneas). */
private data class OfferLine(val id: Long, val number: String = "", val value: String = "") {
    val entry get() = OfferEntry(number, value)
}

/**
 * "Oferta en Ruta" como pantalla completa: líneas cantidad × valor en tarjetas con subtotal, y una barra
 * inferior fija con el total, «Agregar línea» e «Imprimir». La ventana se redimensiona con el teclado
 * (adjustResize mientras la pantalla está abierta), así que la barra queda siempre a la vista sobre él.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfferScreen(onSubmit: (List<OfferEntry>) -> String?, onBack: () -> Unit) {
    AdjustResizeWhileVisible()

    var nextId by remember { mutableStateOf(1L) }
    val lines = remember { mutableStateListOf(OfferLine(0L)) }
    val focus = remember { mutableMapOf<Long, FocusRequester>() }
    var focusTarget by remember { mutableStateOf<Long?>(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    val entries = lines.map { it.entry }
    val total = Offers.total(entries)
    val lineCount = entries.count { Offers.subtotal(it) > 0 }

    fun addLine() {
        val id = nextId++
        lines.add(OfferLine(id))
        focusTarget = id
        error = null
    }

    // Línea nueva (o la primera al abrir): se desplaza hasta ella y pone el foco en su cantidad.
    LaunchedEffect(focusTarget) {
        val id = focusTarget ?: return@LaunchedEffect
        val index = lines.indexOfFirst { it.id == id }
        if (index >= 0) listState.animateScrollToItem(index)
        runCatching { focus[id]?.requestFocus() }
        focusTarget = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Oferta en Ruta", fontWeight = FontWeight.Bold)
                        Text("Un solo comprobante para todas las líneas", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                    }
                },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurayColors.Amber800,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        bottomBar = {
            OfferBottomBar(
                total = total,
                lineCount = lineCount,
                error = error,
                onAdd = ::addLine,
                onPrint = {
                    focusManager.clearFocus()
                    val r = onSubmit(entries)
                    if (r == null) onBack() else error = r
                },
            )
        },
        containerColor = Color(0xFFFFF8EC),
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(lines, key = { _, l -> l.id }) { i, line ->
                val requester = focus.getOrPut(line.id) { FocusRequester() }
                OfferLineCard(
                    index = i,
                    line = line,
                    isLast = i == lines.lastIndex,
                    canRemove = lines.size > 1,
                    quantityFocus = requester,
                    onChange = { lines[i] = it; error = null },
                    onRemove = { lines.removeAt(i); focus.remove(line.id); error = null },
                    onDone = { focusManager.clearFocus() },
                )
            }
        }
    }
}

@Composable
private fun OfferLineCard(
    index: Int,
    line: OfferLine,
    isLast: Boolean,
    canRemove: Boolean,
    quantityFocus: FocusRequester,
    onChange: (OfferLine) -> Unit,
    onRemove: () -> Unit,
    onDone: () -> Unit,
) {
    val subtotal = Offers.subtotal(line.entry)
    val valueFocus = remember { FocusRequester() }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(26.dp).background(SurayColors.Amber100, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("${index + 1}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SurayColors.Amber900) }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (subtotal > 0) "Subtotal $${formatClp(subtotal)}" else "Línea ${index + 1}",
                    fontWeight = FontWeight.SemiBold,
                    color = if (subtotal > 0) SurayColors.Amber900 else Color(0xFF6B7C8C),
                    modifier = Modifier.weight(1f),
                )
                if (canRemove) {
                    IconButton(onRemove) { Icon(Icons.Default.Close, "Quitar línea ${index + 1}", tint = Color(0xFF9E9E9E)) }
                } else {
                    Spacer(Modifier.height(48.dp))
                }
            }
            Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    line.number,
                    { onChange(line.copy(number = it.filter(Char::isDigit).take(4))) },
                    Modifier.weight(2f).focusRequester(quantityFocus),
                    label = { Text("Cantidad") },
                    singleLine = true,
                    shape = SurayFieldShape,
                    colors = surayFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { valueFocus.requestFocus() }),
                )
                Text("×", fontSize = 22.sp, color = Color(0xFF9E9E9E), modifier = Modifier.padding(horizontal = 8.dp))
                OutlinedTextField(
                    line.value,
                    { onChange(line.copy(value = it.filter(Char::isDigit).take(7))) },
                    Modifier.weight(3f).focusRequester(valueFocus),
                    label = { Text("Valor") },
                    prefix = { Text("$ ") },
                    singleLine = true,
                    shape = SurayFieldShape,
                    colors = surayFieldColors(),
                    // En la última línea, «Listo» cierra el teclado; en las demás pasa a la siguiente.
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onDone() }),
                )
            }
        }
    }
}

@Composable
private fun OfferBottomBar(total: Long, lineCount: Int, error: String?, onAdd: () -> Unit, onPrint: () -> Unit) {
    Surface(color = Color.White, shadowElevation = 12.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            error?.let {
                Text(it, color = Color(0xFFD32F2F), fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (lineCount == 1) "Total · 1 línea" else "Total · $lineCount líneas",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7C8C),
                    )
                    Text("$${formatClp(total)}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = SurayColors.Amber900)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onAdd,
                    Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SurayColors.Amber800),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SurayColors.Amber900),
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Agregar línea")
                }
                Button(
                    onPrint,
                    Modifier.weight(1f).height(48.dp),
                    enabled = total > 0,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurayColors.Amber800),
                ) { Text("Imprimir", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/**
 * Pone la ventana en `adjustResize` mientras esta pantalla está visible y restaura el modo anterior al salir:
 * así el teclado encoge el contenido (y la barra inferior sube) sin cambiar el resto de pantallas.
 */
@Composable
private fun AdjustResizeWhileVisible() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val previous = window.attributes.softInputMode
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose { window.setSoftInputMode(previous) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
