package cl.suray.posbus.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.suray.posbus.domain.LockoutPolicy
import cl.suray.posbus.ui.theme.SurayColors
import cl.suray.posbus.ui.vibrateError
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/** Equivale a `LoginScreen`: clave de 6 dígitos. El acceso por NFC se agrega con las integraciones. */
@Composable
fun LoginScreen(verify: (String) -> Boolean, lockout: LockoutPolicy, onSuccess: () -> Unit) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    // Segundos de bloqueo que quedan; el estado vive en las preferencias, así que sobrevive a reabrir la app.
    var remaining by remember { mutableStateOf(lockout.remainingSeconds()) }
    LaunchedEffect(remaining > 0) {
        while (remaining > 0) {
            delay(250)
            remaining = lockout.remainingSeconds()
        }
    }
    val locked = remaining > 0

    fun submit() {
        if (locked) return
        if (verify(password)) {
            lockout.onSuccess()
            onSuccess()
        } else {
            val lockSeconds = lockout.onFailure()
            context.vibrateError(locked = lockSeconds > 0)
            remaining = lockout.remainingSeconds()
            error = true
            password = ""
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SurayColors.Amber800, SurayColors.Amber100))),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
            Card(
                shape = RoundedCornerShape(15.dp),
                elevation = CardDefaults.cardElevation(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Column(
                    Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Icon(Icons.Default.Lock, null, Modifier.size(60.dp), tint = SurayColors.Amber800)
                    Text("Ingrese la Contraseña", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xDD000000))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { if (it.length <= 6) { password = it; error = false } },
                        label = { Text("Contraseña (6 dígitos)") },
                        enabled = !locked,
                        isError = error || locked,
                        supportingText = {
                            when {
                                locked -> Text("Demasiados intentos. Espere $remaining s")
                                error -> Text("Contraseña incorrecta")
                            }
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        textStyle = TextStyle(fontSize = 18.sp, letterSpacing = 8.sp, textAlign = TextAlign.Center),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SurayColors.Amber800,
                            focusedLabelColor = SurayColors.Amber800,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = ::submit,
                        enabled = !locked,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SurayColors.Amber800),
                        elevation = ButtonDefaults.buttonElevation(5.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) { Text("Ingresar", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
