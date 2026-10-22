package cl.suray.posbus.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cl.suray.posbus.AppContainer
import cl.suray.posbus.data.prefs.AppPrefs
import cl.suray.posbus.domain.ButtonColorDefaults
import cl.suray.posbus.domain.ButtonColorSpec
import cl.suray.posbus.domain.ButtonKind
import cl.suray.posbus.ui.home.toColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class ColorItem(val kind: ButtonKind, val index: Int, val label: String, val spec: ButtonColorSpec, val custom: Boolean)

class ButtonColorsViewModel(private val c: AppContainer) : ViewModel() {
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<ColorItem>> = _items

    private fun load(): List<ColorItem> {
        fun item(kind: ButtonKind, i: Int, label: String): ColorItem {
            val saved = c.prefs.buttonColorsJson(ButtonColorSpec.storageKey(kind, i))?.let(ButtonColorSpec::fromJson)
            return ColorItem(kind, i, label, saved ?: ButtonColorDefaults.spec(kind, i), saved != null)
        }
        val week = c.prefs.weekdayFares.mapIndexed { i, t -> item(ButtonKind.Weekday, i, t.nombre) }
        val sun = c.prefs.sundayFares.mapIndexed { i, t -> item(ButtonKind.Sunday, i, t.nombre) }
        return week + sun + listOf(item(ButtonKind.Otros, 0, "Oferta"), item(ButtonKind.Otros, 1, "Cargo"))
    }

    private fun changed() {
        _items.value = load()
        c.settingsRevision.update { it + 1 }
    }

    fun save(item: ColorItem, spec: ButtonColorSpec) {
        c.prefs.setButtonColorsJson(ButtonColorSpec.storageKey(item.kind, item.index), spec.toJson())
        changed()
    }

    fun reset(item: ColorItem) {
        c.prefs.setButtonColorsJson(ButtonColorSpec.storageKey(item.kind, item.index), null)
        changed()
    }

    class Factory(private val c: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ButtonColorsViewModel(c) as T
    }
}

/** Lista de los 14 botones de venta (6 de lunes a sábado, 6 de domingo y Oferta/Cargo) con editor de color. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ButtonColorsScreen(vm: ButtonColorsViewModel, onBack: () -> Unit) {
    val items by vm.items.collectAsState()
    var editing by remember { mutableStateOf<ColorItem?>(null) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Colores de los Botones") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            val groups = listOf(
                ButtonKind.Weekday to "Lunes a sábado",
                ButtonKind.Sunday to "Domingo y feriados",
                ButtonKind.Otros to "Otros",
            )
            groups.forEach { (kind, title) ->
                item { Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)) }
                items(items.filter { it.kind == kind }, key = { "${it.kind}_${it.index}" }) { it ->
                    Row(Modifier.fillMaxWidth().clickable { editing = it }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Swatch(it.spec, Modifier.size(width = 56.dp, height = 36.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(it.label)
                            if (it.custom) Text("Personalizado", fontSize = 12.sp, color = Color(0xFF757575))
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    editing?.let { item ->
        ColorEditorDialog(
            item,
            onSave = { vm.save(item, it); editing = null },
            onReset = { vm.reset(item); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun Swatch(spec: ButtonColorSpec, modifier: Modifier) {
    val c = spec.toColors()
    Box(modifier.background(c.background, RoundedCornerShape(8.dp)).border(BorderStroke(3.dp, c.border), RoundedCornerShape(8.dp)))
}

@Composable
private fun ColorEditorDialog(item: ColorItem, onSave: (ButtonColorSpec) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    var s by remember { mutableStateOf(item.spec) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Swatch(s, Modifier.fillMaxWidth().height(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("Fondo", fontWeight = FontWeight.Bold)
                Channel("Rojo", s.bgR) { s = s.copy(bgR = it) }
                Channel("Verde", s.bgG) { s = s.copy(bgG = it) }
                Channel("Azul", s.bgB) { s = s.copy(bgB = it) }
                Text("Opacidad: ${"%.0f".format(s.bgOpacity * 100)}%", fontSize = 13.sp)
                Slider(s.bgOpacity, { s = s.copy(bgOpacity = it) }, valueRange = 0f..1f)
                Text("Borde", fontWeight = FontWeight.Bold)
                Channel("Rojo", s.borderR) { s = s.copy(borderR = it) }
                Channel("Verde", s.borderG) { s = s.copy(borderG = it) }
                Channel("Azul", s.borderB) { s = s.copy(borderB = it) }
            }
        },
        confirmButton = { TextButton({ onSave(s) }) { Text("Guardar") } },
        dismissButton = {
            Row {
                TextButton(onReset) { Text("Restablecer") }
                TextButton(onDismiss) { Text("Cancelar") }
            }
        },
    )
}

@Composable
private fun Channel(label: String, value: Int, onChange: (Int) -> Unit) {
    Text("$label: $value", fontSize = 13.sp)
    Slider(value.toFloat(), { onChange(it.toInt()) }, valueRange = 0f..255f)
}
