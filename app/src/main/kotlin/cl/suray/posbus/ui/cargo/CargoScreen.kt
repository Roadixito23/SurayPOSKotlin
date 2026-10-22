package cl.suray.posbus.ui.cargo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.suray.posbus.data.prefs.CargoData
import cl.suray.posbus.domain.CargoInput

/** "Cargo Express": datos de la encomienda; al imprimir vuelve a Home, que muestra el resultado. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CargoScreen(onSubmit: (CargoData) -> Unit, onBack: () -> Unit) {
    var destino by remember { mutableStateOf(CargoInput.DESTINATIONS_FIRST) }
    var articulo by remember { mutableStateOf("") }
    var precio by remember { mutableStateOf("") }
    var destinatario by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Cargo Express") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFFF8F00), titleContentColor = Color.White, navigationIconContentColor = Color.White),
        )
    }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Destino: $destino", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton({ destino = CargoInput.toggleDestination(destino) }) { Text("Cambiar") }
                }
            }
            OutlinedTextField(
                articulo, { articulo = it; error = null }, Modifier.fillMaxWidth(), label = { Text("Descripción del Artículo *") }, singleLine = true,
            )
            OutlinedTextField(
                precio, { precio = it.filter(Char::isDigit).take(8); error = null }, Modifier.fillMaxWidth(),
                label = { Text("Precio *") }, prefix = { Text("$ ") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                destinatario, { destinatario = it; error = null }, Modifier.fillMaxWidth(), label = { Text("Nombre del Destinatario *") }, singleLine = true,
            )
            OutlinedTextField(
                telefono, { telefono = it.filter(Char::isDigit).take(CargoInput.MAX_PHONE_DIGITS) }, Modifier.fillMaxWidth(),
                label = { Text("Teléfono (opcional)") }, placeholder = { Text("(Incluir el 9)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
            error?.let { Text(it, color = Color.Red) }
            Button({
                val e = CargoInput.validate(articulo, precio, destinatario)
                if (e != null) error = e
                else onSubmit(CargoData(destino, articulo.trim(), precio.trim().toDouble(), destinatario.trim(), telefono))
            }, Modifier.fillMaxWidth()) { Text("Imprimir Ticket de Cargo") }
        }
    }
}
