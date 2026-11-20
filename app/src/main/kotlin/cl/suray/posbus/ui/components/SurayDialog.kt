package cl.suray.posbus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import cl.suray.posbus.domain.PasswordChange
import cl.suray.posbus.domain.PasswordChangeError
import cl.suray.posbus.ui.theme.SurayColors
import cl.suray.posbus.ui.vibrateError
import kotlinx.coroutines.delay

/** Forma y colores de los campos de las ventanas flotantes (borde y etiqueta ámbar al enfocar). */
val SurayFieldShape = RoundedCornerShape(10.dp)

@Composable
fun surayFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SurayColors.Amber800,
    focusedLabelColor = SurayColors.Amber800,
    cursorColor = SurayColors.Amber800,
)

/**
 * Ventana flotante común: tarjeta blanca, ícono ámbar, título en negrita y botón principal ámbar a todo el ancho,
 * con el estilo del login. Sube con el teclado (el contenido hace scroll en pantallas pequeñas).
 */
@Composable
fun SurayDialogCard(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter = rememberVectorPainter(Icons.Default.Lock),
    confirmEnabled: Boolean = true,
    dismissible: Boolean = true,
    dimBackground: Boolean = true,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        if (!dimBackground) {
            val window = (LocalView.current.parent as? DialogWindowProvider)?.window
            SideEffect { window?.setDimAmount(0f) }
        }
        Card(
            modifier.fillMaxWidth().widthIn(max = 420.dp).padding(horizontal = 20.dp),
            shape = RoundedCornerShape(15.dp),
            elevation = CardDefaults.cardElevation(10.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(icon, null, Modifier.size(48.dp), tint = SurayColors.Amber800)
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xDD000000), textAlign = TextAlign.Center)
                content()
                Button(
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurayColors.Amber800),
                    elevation = ButtonDefaults.buttonElevation(5.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text(confirmText, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                if (dismissible) TextButton(onDismiss) { Text("Cancelar", color = SurayColors.Amber900) }
            }
        }
    }
}

/** Pide el foco para el campo y abre el teclado al mostrarse la ventana. */
@Composable
fun rememberAutoFocus(): FocusRequester {
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(150) // la ventana aún se está montando; en Android 6 pedirlo antes no abre el teclado
        runCatching { requester.requestFocus() }
        keyboard?.show()
    }
    return requester
}

/** Campo de clave: texto oculto, teclado numérico y hasta [MAX_PASSWORD_LENGTH] caracteres. */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    enabled: Boolean = true,
    supporting: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(MAX_PASSWORD_LENGTH)) },
        label = { Text(label) },
        enabled = enabled,
        isError = isError,
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        shape = SurayFieldShape,
        colors = surayFieldColors(),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onImeAction() }, onNext = { onImeAction() }),
        modifier = modifier.fillMaxWidth(),
    )
}

const val MAX_PASSWORD_LENGTH = 12

/**
 * Pide una clave antes de una acción (anular, reimprimir, Ajustes…). Si falla: vibración corta, error y campo vacío;
 * sin bloqueo por intentos (el bloqueo progresivo es solo del login).
 */
@Composable
fun PasswordPromptDialog(
    title: String,
    verify: (String) -> Boolean,
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
    icon: Painter = rememberVectorPainter(Icons.Default.Lock),
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val focus = rememberAutoFocus()

    fun submit() {
        if (verify(input)) onSuccess() else {
            context.vibrateError(locked = false)
            error = true
            input = ""
        }
    }

    SurayDialogCard(title, onCancel, confirmText = "Confirmar", onConfirm = ::submit, icon = icon) {
        PasswordField(
            input, { input = it; error = false }, if (error) "Clave incorrecta" else "Clave",
            Modifier.focusRequester(focus), isError = error, onImeAction = ::submit,
        )
    }
}

/**
 * Cambio de clave con tres campos: actual, nueva y repetir. [onSave] devuelve un motivo de rechazo (o `null` si guardó),
 * que se muestra en la propia ventana.
 */
@Composable
fun ChangePasswordDialog(
    label: String,
    verifyCurrent: (String) -> Boolean,
    exactLength: Boolean,
    onSave: (String) -> String?,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<PasswordChangeError?>(null) }
    var rejection by remember { mutableStateOf<String?>(null) }
    val focus = rememberAutoFocus()

    fun submit() {
        val e = PasswordChange.validate(verifyCurrent(current), new, repeat, exactLength)
        error = e
        if (e != null) {
            context.vibrateError(locked = false)
            if (e == PasswordChangeError.CurrentWrong) current = ""
            return
        }
        rejection = onSave(new)
    }
    fun msg(which: PasswordChangeError) =
        if (error == which) PasswordChange.message(which, exactLength) else null

    SurayDialogCard("Cambiar $label", onCancel, confirmText = "Guardar", onConfirm = ::submit) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PasswordField(
                current, { current = it; error = null }, "Clave actual", Modifier.focusRequester(focus),
                isError = error == PasswordChangeError.CurrentWrong,
                supporting = msg(PasswordChangeError.CurrentWrong), imeAction = ImeAction.Next,
            )
            PasswordField(
                new, { new = it; error = null }, "Nueva clave",
                isError = error == PasswordChangeError.InvalidLength,
                supporting = msg(PasswordChangeError.InvalidLength), imeAction = ImeAction.Next,
            )
            PasswordField(
                repeat, { repeat = it; error = null }, "Repetir nueva clave",
                isError = error == PasswordChangeError.Mismatch,
                supporting = msg(PasswordChangeError.Mismatch), onImeAction = ::submit,
            )
            rejection?.let { Text(it, color = SurayColors.Red700, fontSize = 14.sp) }
        }
    }
}

/** Entrada de un número (p. ej. el ID de terminal); [onSave] devuelve el motivo del rechazo o `null` si guardó. */
@Composable
fun NumberInputDialog(
    title: String,
    label: String,
    initial: String,
    maxLength: Int,
    onSave: (String) -> String?,
    onCancel: () -> Unit,
    icon: Painter = rememberVectorPainter(Icons.Default.Lock),
) {
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = rememberAutoFocus()
    fun submit() { error = onSave(value) }

    SurayDialogCard(title, onCancel, confirmText = "Guardar", onConfirm = ::submit, icon = icon) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it.filter(Char::isDigit).take(maxLength); error = null },
            label = { Text(label) },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            singleLine = true,
            shape = SurayFieldShape,
            colors = surayFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
}
