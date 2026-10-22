package cl.suray.posbus.data.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

enum class PrefType(val tag: String) { String("string"), Bool("bool"), Int("int"), Double("double"), StringList("stringList") }

/** Preferencia tipada. [value]: String, Boolean, Long, Double o List<String>. */
data class PrefEntry(val key: String, val type: PrefType, val value: Any)

/**
 * `preferences.json` del `.suray` de Flutter: mapa plano `clave → valor` más una clave `clave_type`
 * por valor (`string`, `bool`, `int`, `double`, `stringList`) y metadatos `__backup_*`.
 */
object PrefsBackup {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(prefs: Map<String, Any?>, dateIso: String): String {
        val out = LinkedHashMap<String, JsonElement>()
        for ((key, v) in prefs) {
            val (type, element) = when (v) {
                is String -> PrefType.String to JsonPrimitive(v)
                is Boolean -> PrefType.Bool to JsonPrimitive(v)
                is Int -> PrefType.Int to JsonPrimitive(v)
                is Long -> PrefType.Int to JsonPrimitive(v)
                // Float → Double por su texto: 0.8f debe quedar 0.8 y no 0.800000011920929.
                is Float -> PrefType.Double to JsonPrimitive(v.toString().toDouble())
                is Double -> PrefType.Double to JsonPrimitive(v)
                is Set<*> -> PrefType.StringList to JsonArray(v.map { JsonPrimitive(it.toString()) })
                is List<*> -> PrefType.StringList to JsonArray(v.map { JsonPrimitive(it.toString()) })
                else -> continue
            }
            out[key] = element
            out["${key}_type"] = JsonPrimitive(type.tag)
        }
        out["__backup_date"] = JsonPrimitive(dateIso)
        out["__backup_version"] = JsonPrimitive("1.0")
        return JsonObject(out).toString()
    }

    /** Ignora los marcadores de tipo y los metadatos; los valores `null` o ilegibles se descartan. */
    fun decode(text: String): List<PrefEntry> {
        val root = json.parseToJsonElement(text) as? JsonObject ?: return emptyList()
        return root.mapNotNull { (key, element) ->
            if (key.endsWith("_type") || key.startsWith("__") || element is JsonNull) return@mapNotNull null
            val declared = (root["${key}_type"] as? JsonPrimitive)?.content
            toEntry(key, element, declared)
        }
    }

    private fun toEntry(key: String, e: JsonElement, declared: String?): PrefEntry? {
        if (e is JsonArray) return PrefEntry(key, PrefType.StringList, e.map { (it as? JsonPrimitive)?.content ?: it.toString() })
        val p = e as? JsonPrimitive ?: return null
        return when (declared) {
            "bool" -> p.booleanOrNull?.let { PrefEntry(key, PrefType.Bool, it) }
            "int" -> (p.longOrNull ?: p.content.toLongOrNull())?.let { PrefEntry(key, PrefType.Int, it) }
            "double" -> (p.doubleOrNull ?: p.content.toDoubleOrNull())?.let { PrefEntry(key, PrefType.Double, it) }
            "string" -> PrefEntry(key, PrefType.String, p.content)
            else -> when { // sin marcador: se infiere del JSON
                p.isString -> PrefEntry(key, PrefType.String, p.content)
                p.booleanOrNull != null -> PrefEntry(key, PrefType.Bool, p.booleanOrNull!!)
                p.longOrNull != null -> PrefEntry(key, PrefType.Int, p.longOrNull!!)
                p.doubleOrNull != null -> PrefEntry(key, PrefType.Double, p.doubleOrNull!!)
                else -> null
            }
        }
    }
}
