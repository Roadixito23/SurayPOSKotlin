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
