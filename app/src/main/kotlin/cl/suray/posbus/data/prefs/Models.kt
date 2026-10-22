package cl.suray.posbus.data.prefs

import kotlinx.serialization.Serializable

/** Tarifa de pasaje (`pasajes` / `sunday_pasajes`). */
@Serializable
data class Tarifa(val nombre: String, val precio: Double)

/** Venta de la caja abierta (`reporte_caja_transactions`). */
@Serializable
data class OpenTransaction(
    val id: Int,
    val nombre: String,
    val valor: Double,
    val comprobante: String,
    val dia: String,
    val mes: String,
    /** Ventas antiguas de Flutter pueden no traer año: se asume el año actual. */
    val ano: String? = null,
    val hora: String,
    val timestamp: Long,
)

/**
 * Última venta (`lastTransaction`). Flutter guarda además campos de cargo y oferta
 * (`destinatario`, `offerEntries`…); se agregan cuando se porten esas pantallas.
 */
@Serializable
data class LastTransaction(
    val nombre: String,
    val valor: Double,
    val comprobante: String,
    val switchValue: Boolean = false,
    /** `ofertaMultiple` en ofertas (mismo valor que Flutter). */
    val tipo: String? = null,
    val offerEntries: List<OfferEntry>? = null,
    val destinatario: String? = null,
    val articulo: String? = null,
    val destino: String? = null,
    val telefono: String? = null,
) {
    val isCargo: Boolean get() = nombre.lowercase().contains("cargo")
    val isOffer: Boolean get() = nombre == "Oferta Ruta" || tipo == "ofertaMultiple"

    fun cargoData(): CargoData? =
        if (isCargo && destinatario != null && articulo != null) {
            CargoData(destino ?: "", articulo, valor, destinatario, telefono ?: "")
        } else {
            null
        }
}

/** Línea de una oferta: cantidad y valor tal como se escribieron. */
@Serializable
data class OfferEntry(val number: String, val value: String)

/** Datos de un cargo (encomienda). */
data class CargoData(val destino: String, val articulo: String, val precio: Double, val destinatario: String, val telefono: String)
