package cl.suray.posbus.domain

import java.util.Calendar
import java.util.TimeZone

fun interface Clock {
    fun nowMillis(): Long
}

object SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

/**
 * Fecha civil sin zona horaria. `java.time` no existe en API 23, así que esto vive aquí
 * y también corre en tests JVM.
 */
data class LocalDay(val year: Int, val month: Int, val day: Int) : Comparable<LocalDay> {

    /** Días desde 1970-01-01 (algoritmo days-from-civil de H. Hinnant). */
    fun toEpochDay(): Long {
        val y = if (month <= 2) year - 1L else year.toLong()
        val era = Math.floorDiv(y, 400L)
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    /** 1 = lunes … 7 = domingo (igual que `DateTime.weekday` en Dart). */
    val dayOfWeek: Int get() = Math.floorMod(toEpochDay() + 3, 7L).toInt() + 1

    fun minusDays(n: Int): LocalDay = fromEpochDay(toEpochDay() - n)

    fun daysUntil(other: LocalDay): Long = other.toEpochDay() - toEpochDay()

    /** yyyy-MM-dd */
    fun iso(): String = "%04d-%02d-%02d".format(year, month, day)

    override fun compareTo(other: LocalDay): Int = toEpochDay().compareTo(other.toEpochDay())

    companion object {
        fun fromEpochDay(epochDay: Long): LocalDay {
            val z = epochDay + 719468
            val era = Math.floorDiv(z, 146097L)
            val doe = z - era * 146097
            val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
            val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
            val y = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
            return LocalDay(y, m, d)
        }

        fun parse(iso: String): LocalDay? {
            val p = iso.split('-')
            if (p.size != 3) return null
            val y = p[0].toIntOrNull() ?: return null
            val m = p[1].toIntOrNull() ?: return null
            val d = p[2].toIntOrNull() ?: return null
            return LocalDay(y, m, d)
        }

        fun of(millis: Long, tz: TimeZone = TimeZone.getDefault()): LocalDay {
            val c = Calendar.getInstance(tz).apply { timeInMillis = millis }
            return LocalDay(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        }
    }
}

/** Partes de fecha/hora tal como las guarda la caja (`dd`, `MM`, `yyyy`, `HH:mm`). */
data class DateParts(val dia: String, val mes: String, val ano: String, val hora: String) {
    companion object {
        fun of(millis: Long, tz: TimeZone = TimeZone.getDefault()): DateParts {
            val c = Calendar.getInstance(tz).apply { timeInMillis = millis }
            return DateParts(
                dia = "%02d".format(c.get(Calendar.DAY_OF_MONTH)),
                mes = "%02d".format(c.get(Calendar.MONTH) + 1),
                ano = "%04d".format(c.get(Calendar.YEAR)),
                hora = "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)),
            )
        }
    }
}

val DAYS_ES_UPPER = listOf("LUNES", "MARTES", "MIÉRCOLES", "JUEVES", "VIERNES", "SÁBADO", "DOMINGO")
val DAYS_ES_SHORT = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
