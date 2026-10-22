package cl.suray.posbus.domain

import java.net.HttpURLConnection
import java.net.URL
import java.util.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

data class HolidayCheckResult(val isSpecial: Boolean, val reason: String? = null)

/** Devuelve el cuerpo JSON del año o null si la consulta falló. */
fun interface HolidayFetcher {
    fun fetch(year: Int): String?
}

/** `https://apis.digital.gob.cl/fl/feriados/{año}`, timeout de conexión 8 s. Requiere permiso INTERNET. */
object HttpHolidayFetcher : HolidayFetcher {
    private const val API_BASE = "https://apis.digital.gob.cl/fl/feriados"

    override fun fetch(year: Int): String? = runCatching {
        val conn = (URL("$API_BASE/$year").openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode == 200) conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } else null
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}

/**
 * Domingo o feriado chileno, con caché anual:
 *  - definitivo si se obtuvo el 5 de enero o después (vale todo el año);
 *  - fungible si se obtuvo antes del 5 de enero: se renueva al llegar a esa fecha.
 * Si la API falla se usa el caché vencido; sin caché, la lista queda vacía.
 */
class HolidayService(
    private val store: HolidayStore,
    private val fetcher: HolidayFetcher = HttpHolidayFetcher,
    private val clock: Clock = SystemClock,
    private val timeZone: TimeZone = TimeZone.getDefault(),
) {
    fun checkToday(): HolidayCheckResult {
        val today = LocalDay.of(clock.nowMillis(), timeZone)
        if (today.dayOfWeek == 7) return HolidayCheckResult(true, "Domingo")

        val match = holidaysFor(today.year).firstOrNull { it.fecha == today.iso() }
            ?: return HolidayCheckResult(false, null)
        return HolidayCheckResult(true, match.nombre ?: "Feriado")
    }

    /** Descarta el caché del año actual y vuelve a consultar. */
    fun refresh(): HolidayCheckResult {
        store.clearHolidays(LocalDay.of(clock.nowMillis(), timeZone).year)
        return checkToday()
    }

    private class Holiday(val fecha: String?, val nombre: String?)

    private fun holidaysFor(year: Int): List<Holiday> {
        val cached = store.holidaysCache(year)
        if (cached != null && !fungibleExpired(year, store.holidaysFungible(year))) return decode(cached)
        return fetchAndCache(year)
    }

    private fun jan5(year: Int) = LocalDay(year, 1, 5)

    private fun today() = LocalDay.of(clock.nowMillis(), timeZone)

    private fun fungibleExpired(year: Int, fungible: Boolean): Boolean =
        fungible && today() >= jan5(year)

    private fun fetchAndCache(year: Int): List<Holiday> {
        val body = fetcher.fetch(year)
        if (body != null) {
            store.setHolidaysCache(year, body)
            store.setHolidaysFungible(year, today() < jan5(year))
            return decode(body)
        }
        return store.holidaysCache(year)?.let(::decode) ?: emptyList()
    }

    private fun decode(raw: String): List<Holiday> = runCatching {
        (Json.parseToJsonElement(raw) as JsonArray).map {
            val o = it as JsonObject
            Holiday(o["fecha"]?.jsonPrimitive?.content, o["nombre"]?.jsonPrimitive?.content)
        }
    }.getOrDefault(emptyList())
}
