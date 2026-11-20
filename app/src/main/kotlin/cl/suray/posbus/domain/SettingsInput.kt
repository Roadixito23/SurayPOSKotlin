package cl.suray.posbus.domain

/** Validación de los campos de Ajustes (las mismas reglas que Flutter). */
object SettingsInput {
    const val MIN_TERMINAL_ID = 1
    const val MAX_TERMINAL_ID = 99
    const val MAX_PDF_MARGIN = 200f

    /** ID de terminal 1-99; `null` si no es válido. */
    fun terminalId(text: String): Int? =
        text.trim().toIntOrNull()?.takeIf { it in MIN_TERMINAL_ID..MAX_TERMINAL_ID }

    /** Precio en pesos: entero mayor que 0. Acepta separadores de miles (`.`), como se escribe en Chile. */
    fun price(text: String): Double? =
        text.trim().replace(".", "").toIntOrNull()?.takeIf { it > 0 }?.toDouble()

    fun fareName(text: String): String? = text.trim().takeIf { it.isNotEmpty() }

    fun affiliateKey(text: String): String? = text.trim().takeIf { it.isNotEmpty() }

    /** Margen final del PDF: 0..[MAX_PDF_MARGIN]. */
    fun pdfMargin(text: String): Float? =
        text.trim().replace(',', '.').toFloatOrNull()?.takeIf { it in 0f..MAX_PDF_MARGIN }
}

/** Motivo por el que un cambio de clave se rechaza. */
enum class PasswordChangeError { CurrentWrong, InvalidLength, Mismatch }

/** Validación pura del formulario «Cambiar clave» (clave actual + nueva + repetir). */
object PasswordChange {
    /**
     * Orden de comprobación: primero la clave actual, luego el largo de la nueva y por último que coincida con la repetición.
     * @param exactLength `true` para la clave del comprobante (exactamente 6 tras `trim`); si no, al menos 6 caracteres.
     * @return `null` si el cambio es válido.
     */
    fun validate(currentMatches: Boolean, new: String, repeat: String, exactLength: Boolean): PasswordChangeError? = when {
        !currentMatches -> PasswordChangeError.CurrentWrong
        !lengthOk(new, exactLength) -> PasswordChangeError.InvalidLength
        new != repeat -> PasswordChangeError.Mismatch
        else -> null
    }

    private fun lengthOk(new: String, exactLength: Boolean) =
        if (exactLength) new.trim().length == PasswordManager.COMPROBANTE_LENGTH else new.length >= PasswordManager.MIN_LENGTH

    fun message(error: PasswordChangeError, exactLength: Boolean) = when (error) {
        PasswordChangeError.CurrentWrong -> "Clave actual incorrecta"
        PasswordChangeError.InvalidLength ->
            if (exactLength) "Debe tener exactamente ${PasswordManager.COMPROBANTE_LENGTH} caracteres"
            else "Debe tener al menos ${PasswordManager.MIN_LENGTH} caracteres"
        PasswordChangeError.Mismatch -> "Las claves no coinciden"
    }
}
