package cl.suray.posbus.domain

import java.util.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/** `pagos_tarjeta_yyyy-MM-dd` (lista JSON de `{monto, txCode, fecha, tipo}`). */
interface CardPaymentStore {
    fun cardPayments(date: String): String?
    fun setCardPayments(date: String, value: String)
}

class CardPayments(
    private val store: CardPaymentStore,
    private val clock: Clock = SystemClock,
    private val timeZone: TimeZone = TimeZone.getDefault(),
) {
    private fun today() = LocalDay.of(clock.nowMillis(), timeZone).iso()

    private fun read(): List<JsonObject> =
        store.cardPayments(today())?.let { raw ->
            runCatching { (Json.parseToJsonElement(raw) as JsonArray).map { it as JsonObject } }.getOrNull()
        } ?: emptyList()

    fun add(monto: Double, txCode: String, fecha: String) {
        val entry = buildJsonObject {
            put("monto", monto)
            put("txCode", txCode)
            put("fecha", fecha)
            put("tipo", "tarjeta")
        }
        store.setCardPayments(today(), JsonArray(read() + entry).toString())
    }

    fun totalToday(): Double = read().sumOf { (it["monto"] as? JsonPrimitive)?.doubleOrNull ?: 0.0 }
}
