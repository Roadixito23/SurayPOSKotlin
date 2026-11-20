package cl.suray.posbus.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import cl.suray.posbus.domain.LockoutPolicy
import cl.suray.posbus.ui.components.PasswordField
import cl.suray.posbus.ui.components.SurayDialogCard
import cl.suray.posbus.ui.components.rememberAutoFocus
import cl.suray.posbus.ui.theme.SurayColors
import cl.suray.posbus.ui.vibrateError
import kotlinx.coroutines.delay

/**
 * Equivale a `LoginScreen`: tarjeta flotante (la misma ventana que el resto de las claves) sobre el degradado ámbar.
 * Mantiene el bloqueo progresivo con cuenta atrás. El acceso por NFC se agrega con las integraciones.
 */
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
    val focus = rememberAutoFocus()
    val keyboard = LocalSoftwareKeyboardController.current
    // Al terminar el bloqueo el campo se habilita sin foco: se lo devolvemos para que el teclado vuelva solo.
    LaunchedEffect(locked) {
        if (!locked) {
            delay(100)
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }

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
    )
    SurayDialogCard(
        title = "Ingrese la Contraseña",
        onDismiss = {},
        confirmText = "Ingresar",
        onConfirm = ::submit,
        confirmEnabled = !locked,
        dismissible = false,
        dimBackground = false,
    ) {
        PasswordField(
            password, { password = it; error = false }, if (error) "Clave incorrecta" else "Clave",
            Modifier.focusRequester(focus),
            isError = error || locked, enabled = !locked,
            supporting = if (locked) "Demasiados intentos. Espere $remaining s" else null,
            onImeAction = ::submit,
        )
    }
}
