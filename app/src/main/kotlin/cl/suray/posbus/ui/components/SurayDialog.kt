package cl.suray.posbus.ui.components

import android.graphics.Rect
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
    withKeyboard: Boolean = true,
    error: String? = null,
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
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            if (!dimBackground) window?.setDimAmount(0f)
            // La ventana se reduce con el teclado (sube la tarjeta) y, si hay campos, lo pide al abrirse.
            window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    if (withKeyboard) WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                    else WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN,
            )
        }
        // Alto que queda libre sobre el teclado: la tarjeta no lo supera y su contenido se desplaza.
        val visibleHeight = rememberVisibleHeight()
        Card(
            modifier.fillMaxWidth().widthIn(max = 420.dp).heightIn(max = visibleHeight - 16.dp).padding(horizontal = 20.dp),
            shape = RoundedCornerShape(15.dp),
            elevation = CardDefaults.cardElevation(10.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Con el teclado abierto queda poco alto: sin el ícono caben los campos, el error y el foco.
                val compact = visibleHeight < 480.dp
                if (!compact) Icon(icon, null, Modifier.size(48.dp), tint = SurayColors.Amber800)
                Text(title, fontSize = if (compact) 18.sp else 22.sp, fontWeight = FontWeight.Bold, color = Color(0xDD000000), textAlign = TextAlign.Center)
                // El error va bajo el título, no junto al campo: con el teclado abierto lo de abajo queda fuera de vista.
                error?.let { Text(it, color = SurayColors.Red700, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center) }
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

/**
 * Alto visible de la ventana (sin el teclado). En Android 6 la ventana del diálogo no se reduce lo bastante por sí sola
 * y una tarjeta alta quedaba por debajo del teclado; se mide con el rectángulo visible y se actualiza al abrir o cerrar el teclado.
 */
@Composable
private fun rememberVisibleHeight(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    var height by remember { mutableStateOf(10_000.dp) }
    DisposableEffect(view) {
        val root = view.rootView
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val r = Rect()
            root.getWindowVisibleDisplayFrame(r)
            if (r.height() > 0) height = with(density) { r.height().toDp() }
        }
        root.viewTreeObserver.addOnGlobalLayoutListener(listener)
        listener.onGlobalLayout()
        onDispose { root.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    return height
}

/**
 * Pide el foco para el campo y abre el teclado al mostrarse la ventana. En Android 6, si el campo toma el foco antes de
 * que la ventana tenga el foco de entrada, el teclado no se abre y no se vuelve a pedir; por eso se suelta y se recupera
 * el foco una vez montada la ventana, lo que reinicia la sesión de entrada.
 */
@Composable
fun rememberAutoFocus(): FocusRequester {
    val requester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { requester.requestFocus() }
        delay(350)
        focusManager.clearFocus(force = true)
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
    val focusManager = LocalFocusManager.current
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
        keyboardActions = KeyboardActions(
            onDone = { onImeAction() },
            onNext = { focusManager.moveFocus(FocusDirection.Next) },
        ),
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
    val message = error?.let { PasswordChange.message(it, exactLength) } ?: rejection
    SurayDialogCard("Cambiar $label", onCancel, confirmText = "Guardar", onConfirm = ::submit, error = message) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PasswordField(
                current, { current = it; error = null }, "Clave actual", Modifier.focusRequester(focus),
                isError = error == PasswordChangeError.CurrentWrong, imeAction = ImeAction.Next,
            )
            PasswordField(
                new, { new = it; error = null }, "Nueva clave",
                isError = error == PasswordChangeError.InvalidLength, imeAction = ImeAction.Next,
            )
            PasswordField(
                repeat, { repeat = it; error = null }, "Repetir nueva clave",
                isError = error == PasswordChangeError.Mismatch, onImeAction = ::submit,
            )
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

    SurayDialogCard(title, onCancel, confirmText = "Guardar", onConfirm = ::submit, icon = icon, error = error) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it.filter(Char::isDigit).take(maxLength); error = null },
            label = { Text(label) },
            isError = error != null,
            singleLine = true,
            shape = SurayFieldShape,
            colors = surayFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
}
