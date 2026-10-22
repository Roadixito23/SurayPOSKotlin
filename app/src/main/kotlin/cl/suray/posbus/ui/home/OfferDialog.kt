package cl.suray.posbus.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cl.suray.posbus.data.prefs.OfferEntry
import cl.suray.posbus.domain.Offers
import cl.suray.posbus.domain.formatClp

private val Amber = Color(0xFFFF8F00)

/** "Oferta en Ruta": varias líneas cantidad × valor; imprime un único comprobante. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfferDialog(onSubmit: (List<OfferEntry>) -> String?, onClose: () -> Unit) {
    val lines = remember { mutableStateListOf(OfferEntry("", "")) }
    var error by remember { mutableStateOf<String?>(null) }
    val total = Offers.total(lines)

    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Scaffold(
            Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("Oferta en Ruta") },
                    actions = { IconButton(onClose) { Icon(Icons.Default.Close, "Cerrar") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Amber, titleContentColor = Color.White, actionIconContentColor = Color.White),
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).padding(16.dp)) {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(lines) { i, line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            OutlinedTextField(
                                line.number, { lines[i] = line.copy(number = it.filter(Char::isDigit).take(4)); error = null },
                                Modifier.weight(2f), label = { Text("Cantidad") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                            OutlinedTextField(
                                line.value, { lines[i] = line.copy(value = it.filter(Char::isDigit).take(7)); error = null },
                                Modifier.weight(3f), label = { Text("Valor") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                            if (lines.size > 1) {
                                IconButton({ lines.removeAt(i) }) { Icon(Icons.Default.Delete, "Quitar línea", tint = Color.Red) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 16.dp)) {
                    Text("Total:", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Amber, modifier = Modifier.weight(1f))
                    Text("$${formatClp(total)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Amber)
                }
                error?.let { Text(it, color = Color.Red, modifier = Modifier.padding(bottom = 8.dp)) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button({ lines.add(OfferEntry("", "")) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Agregar línea")
                    }
                    Button(
                        { val r = onSubmit(lines.toList()); if (r == null) onClose() else error = r },
                        colors = ButtonDefaults.buttonColors(containerColor = Amber),
                    ) { Text("Imprimir") }
                }
            }
        }
    }
}
