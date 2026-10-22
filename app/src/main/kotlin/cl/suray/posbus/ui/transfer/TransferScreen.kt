package cl.suray.posbus.ui.transfer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.suray.posbus.R
import cl.suray.posbus.ui.theme.Hemiheads

private val Green = Color(0xFF355E3B)
private val GreenDark = Color(0xFF1E3B22)
private val GreenLight = Color(0xFF4A7A50)
private val GreenSurface = Color(0xFFEAF2EB)

private val BANK_DATA = listOf(
    "Nombre" to "TRANSPORTE SURAY LTDA",
    "RUT" to "77799670-3",
    "Banco" to "BCI",
    "Tipo" to "Cta. Corriente",
    "Cuenta" to "95018948",
    "Asunto" to "PASAJE",
    "Correo" to "ARIEL@SURAY.CL",
)
private const val WHATSAPP = "+56 9 4583 4172"

/** Datos de transferencia bancaria y QR (datos del banco y WhatsApp para enviar el comprobante). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = Color(0xFFF2F6F2),
        topBar = {
            TopAppBar(
                title = { Text("TRANSFERENCIA", fontFamily = Hemiheads, letterSpacing = 1.5.sp) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Green, titleContentColor = Color.White, navigationIconContentColor = Color.White),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.weight(57f).fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(5.dp)) {
                Row(Modifier.fillMaxWidth().background(GreenDark).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Datos de Transferencia", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                    BANK_DATA.forEachIndexed { i, (label, value) -> DataRow(label, value, even = i % 2 == 0) }
                }
            }
            Card(Modifier.weight(40f).fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(5.dp)) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(8.dp).fillMaxSize().background(GreenDark))
                    QrColumn("Escanea los Datos", R.drawable.qr_suray, null, Modifier.weight(1f))
                    QrColumn("Envía tu comprobante a WhatsApp", R.drawable.wspqr, WHATSAPP, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DataRow(label: String, value: String, even: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(if (even) GreenSurface else Color.White, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.width(80.dp), fontWeight = FontWeight.Bold, color = GreenDark, fontSize = 16.sp)
        Box(Modifier.width(2.dp).height(20.dp).background(GreenLight, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Text(value, color = Color(0xFF424242), fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun QrColumn(title: String, image: Int, phone: String?, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(title, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = GreenDark, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Image(painterResource(image), title, Modifier.weight(1f).fillMaxWidth(), contentScale = ContentScale.Fit)
        phone?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                it, fontWeight = FontWeight.ExtraBold, color = Green, fontSize = 13.sp, letterSpacing = 0.5.sp,
                modifier = Modifier.background(GreenSurface, RoundedCornerShape(6.dp)).border(1.dp, GreenLight, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
