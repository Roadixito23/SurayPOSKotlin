package cl.suray.posbus.domain

import cl.suray.posbus.data.prefs.OfferEntry

/** Cálculo y validación de la oferta en ruta (cantidad de pasajeros × valor por línea). */
object Offers {
    fun quantity(e: OfferEntry): Long = e.number.trim().toLongOrNull() ?: 0L
    fun price(e: OfferEntry): Long = e.value.trim().replace(".", "").toLongOrNull() ?: 0L
    fun subtotal(e: OfferEntry): Long = quantity(e) * price(e)
    fun total(entries: List<OfferEntry>): Long = entries.sumOf(::subtotal)

    sealed interface Result {
        data class Ok(val entries: List<OfferEntry>, val total: Long) : Result
        data class Invalid(val reason: String) : Result
    }

    /** Descarta las líneas totalmente vacías; las demás deben tener cantidad y valor mayores que 0. */
    fun validate(entries: List<OfferEntry>): Result {
        val filled = entries.filter { it.number.isNotBlank() || it.value.isNotBlank() }
        if (filled.isEmpty()) return Result.Invalid("Ingrese al menos una línea")
        if (filled.any { quantity(it) <= 0 || price(it) <= 0 }) return Result.Invalid("Cada línea necesita cantidad y valor mayores que 0")
        return Result.Ok(filled, total(filled))
    }
}

object CargoInput {
    const val DESTINATIONS_FIRST = "Aysén"
    const val DESTINATIONS_SECOND = "Coyhaique"
    const val MAX_PHONE_DIGITS = 9

    fun toggleDestination(current: String) = if (current == DESTINATIONS_FIRST) DESTINATIONS_SECOND else DESTINATIONS_FIRST

    /** Devuelve el mensaje de error o `null` si los datos son válidos. El teléfono es opcional. */
    fun validate(articulo: String, precio: String, destinatario: String): String? {
        if (articulo.isBlank() || precio.isBlank() || destinatario.isBlank()) return "Completa los campos obligatorios"
        if ((precio.trim().toLongOrNull() ?: 0L) <= 0L) return "El precio debe ser mayor a cero"
        return null
    }
}
