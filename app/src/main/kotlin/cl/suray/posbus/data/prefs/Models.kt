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
)
