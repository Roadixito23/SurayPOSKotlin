package cl.suray.posbus.domain

import cl.suray.posbus.data.prefs.Ruta

/** Sentido actual de una ruta: de dónde sale y hacia dónde va el bus. */
data class Direction(val origen: String, val destino: String)

/**
 * Reglas de rutas: sentido, línea impresa en el ticket y validación de nombres.
 *
 * Largo máximo ([NAME_MAX]): medido con las fuentes Roboto del Sunmi V1s (720 px, 320 dpi,
 * escala de fuente 0,85) y un nombre realista ("Puerto Ingeniero Ibáñez…") recortado a N:
 *  1. botón "Destino: xxx" del Home (~266 dp útiles): cabe holgado hasta N ≈ 40;
 *  2. ticket pasajero "Destino: xxx" a 9 pt (158 pt útiles): sin reducir hasta N ≈ 30;
 *  3. ticket "Intermedio xxx - yyy" (dos nombres, el caso estrecho): sin reducir hasta N = 13;
 *     con N = 16 `PdfPen.centered` lo baja a ~7,9 pt (todo en MAYÚSCULAS, ~6,7 pt).
 * Se fija N = 16 para admitir nombres como "Puerto Chacabuco" sin que el intermedio baje de [TICKET_MIN_PT].
 */
object Routes {
    const val NAME_MAX = 16
    const val TICKET_TEXT_PT = 9f
    const val TICKET_MIN_PT = 7.5f

    fun direction(ruta: Ruta, invertida: Boolean): Direction =
        if (invertida) Direction(ruta.destino, ruta.origen) else Direction(ruta.origen, ruta.destino)

    /** Misma regla que agrupa la sección "Intermedio" del cierre de caja. */
    fun isIntermedio(nombre: String): Boolean {
        val l = nombre.lowercase()
        return l.contains("int. hasta") || (l.contains("escolar") && l.contains("intermedio"))
    }

    fun destinoLabel(d: Direction) = "Destino: ${d.destino}"

    fun intermedioLabel(d: Direction) = "Intermedio ${d.origen} - ${d.destino}"

    /** Línea de ruta del ticket: el intermedio omite el destino y muestra el tramo completo. */
    fun ticketLine(nombreTarifa: String, d: Direction): String =
        if (isIntermedio(nombreTarifa)) intermedioLabel(d) else destinoLabel(d)

    /** null si la ruta es válida; si no, el motivo para mostrar al usuario. */
    fun validate(origen: String, destino: String): String? {
        val o = origen.trim()
        val d = destino.trim()
        return when {
            o.isEmpty() || d.isEmpty() -> "Ingrese origen y destino"
            o.length > NAME_MAX || d.length > NAME_MAX -> "Máximo $NAME_MAX caracteres por nombre"
            o.equals(d, ignoreCase = true) -> "Origen y destino deben ser distintos"
            else -> null
        }
    }
}
