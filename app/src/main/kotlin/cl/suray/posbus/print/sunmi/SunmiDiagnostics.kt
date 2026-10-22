package cl.suray.posbus.print.sunmi

import android.os.SystemClock
import android.util.Log
import cl.suray.posbus.data.prefs.OpenTransaction
import cl.suray.posbus.domain.CashReport
import cl.suray.posbus.domain.Clock
import cl.suray.posbus.domain.DateParts
import cl.suray.posbus.domain.LocalDay
import cl.suray.posbus.domain.PrintFailure
import cl.suray.posbus.domain.ReportDate
import cl.suray.posbus.domain.ReportPrinter

/** Laboratorio de la Fase 1: lee información del servicio e imprime una página de prueba, registrando cada paso. */
class SunmiDiagnostics(
    private val client: SunmiPrinterClient,
    private val reportPrinter: ReportPrinter,
    private val clock: Clock,
    private val log: (String) -> Unit,
) {

    private fun emit(line: String) { Log.d(TAG, line); log(line) }

    private suspend fun step(label: String, block: suspend () -> CallResult): Boolean {
        val r = try { block() } catch (e: SunmiUnavailableException) { emit("✗ $label → ${e.message}"); return false }
        emit((if (r.ok) "✓ " else "✗ ") + "$label → ${r.summary}")
        return r.ok
    }

    /** Información y candidatos a «estado de la impresora» (solo getters `()I` / `()Z` / `()String`; no toca papel). */
    suspend fun info() {
        emit("── Información ──")
        val svc = try { client.connect() } catch (e: SunmiUnavailableException) { emit("✗ ${e.message}"); return }
        fun q(name: String, f: () -> Any?) = emit("$name = " + (runCatching { f() }.getOrElse { "error: ${it.message}" }))
        q("getServiceVersion") { svc.serviceVersion }
        q("getPrinterSerialNo") { svc.printerSerialNo }
        q("getPrinterVersion (código 7)") { svc.printerVersion }
        q("getPrinterModal (código 8)") { svc.printerModal }
        emit("Candidatos a estado (comparar con y sin papel / tapa abierta):")
        q("  unknown2()") { svc.unknown2() }
        q("  updatePrinterState() [32] (1=normal, 4=sin papel)") { svc.updatePrinterState() }
        q("  unknown34()") { svc.unknown34() }
        q("  unknown35()") { svc.unknown35() }
        q("  unknown36()") { svc.unknown36() }
        q("  unknown37()") { svc.unknown37() }
        q("  unknown38()") { svc.unknown38() }
        q("  unknown39()") { svc.unknown39() }
        q("  unknown40()") { svc.unknown40() }
        q("  unknown42()") { svc.unknown42() }
        q("  unknown45()") { svc.unknown45() }
        step("getPrintedLength") { client.call { getPrintedLength(it) } }
    }

    /** Página de prueba: caracteres, ancho, alineación, tamaños, ESC/POS y columnas. Gasta ~25 cm de papel. */
    suspend fun testPage() {
        emit("── Página de prueba ──")
        if (!step("printerInit") { client.call { printerInit(it) } }) return

        suspend fun text(s: String) = step("texto «${s.trim().take(24)}»") { client.call { printText(s, it) } }
        suspend fun align(a: Int) = step("alineación $a") { client.call { setAlignment(a, it) } }
        suspend fun size(f: Float) = step("tamaño $f") { client.call { setFontSize(f, it) } }
        suspend fun raw(vararg b: Int) = step("RAW " + b.joinToString(" ") { "%02X".format(it) }) { client.call { sendRAWData(ByteArray(b.size) { i -> b[i].toByte() }, it) } }

        align(1); size(30f); text("PRUEBA SUNMI\n"); size(24f)
        text("Impresion directa V1s\n"); align(0)

        text("\n-- 1. CARACTERES --\n")
        text("SÁBADO MIÉRCOLES Aysén\n")
        text("Gestión ñ Ñ ü ¿? ¡! \n")
        text("áéíóú ÁÉÍÓÚ\n")
        text("\$3.600 \$1.234.567 N° 01-000123\n")

        text("\n-- 2. ANCHO (cuenta cuantos digitos caben por linea) --\n")
        for (f in listOf(20f, 24f, 28f)) {
            size(f); text("Tamano ${f.toInt()}:\n")
            text("1234567890".repeat(6) + "\n")
        }
        size(24f)

        text("\n-- 3. ALINEACION --\n")
        align(0); text("IZQUIERDA\n"); align(1); text("CENTRO\n"); align(2); text("DERECHA\n"); align(0)

        text("\n-- 4. TAMANOS --\n")
        for (f in listOf(18f, 22f, 26f, 32f)) { size(f); text("Tam ${f.toInt()}: Pasajeros\n") }
        size(24f)

        text("\n-- 5. ESC/POS (RAW) --\n")
        raw(0x1B, 0x45, 0x01); text("NEGRITA ACTIVA\n"); raw(0x1B, 0x45, 0x00); text("negrita apagada\n")
        raw(0x1D, 0x42, 0x01); text(" TEXTO INVERTIDO \n"); raw(0x1D, 0x42, 0x00)
        raw(0x1D, 0x21, 0x11); text("DOBLE\n"); raw(0x1D, 0x21, 0x00)
        raw(0x1B, 0x2D, 0x01); text("subrayado\n"); raw(0x1B, 0x2D, 0x00)

        text("\n-- 6. COLUMNAS printColumnsText --\n")
        val head = arrayOf("N°C", "Dia", "Hora", "Tipo", "Valor")
        val row = arrayOf("12", "22", "17:23", "PG", "\$3.600")
        val widthsChars = intArrayOf(4, 4, 6, 7, 11)
        val aligns = intArrayOf(0, 0, 0, 0, 2)
        step("columnas texto (cabecera)") { client.call { printColumnsText(head, widthsChars, aligns, it) } }
        step("columnas texto (fila)") { client.call { printColumnsText(row, widthsChars, aligns, it) } }

        text("\n-- 7. COLUMNAS printColumnsString --\n")
        step("columnas string (cabecera)") { client.call { printColumnsString(head, widthsChars, aligns, it) } }
        step("columnas string (fila)") { client.call { printColumnsString(row, widthsChars, aligns, it) } }
        text("\n-- 7b. COLUMNAS (anchos 1:1:2:2:3) --\n")
        val prop = intArrayOf(1, 1, 2, 2, 3)
        step("columnas texto proporcional") { client.call { printColumnsText(row, prop, aligns, it) } }
        step("columnas string proporcional") { client.call { printColumnsString(row, prop, aligns, it) } }

        text("\n-- FIN --\n")
        step("lineWrap(4)") { client.call { lineWrap(4, it) } }
        emit("Página enviada. Revisa el papel con el formulario de la guía.")
    }

    /** Prueba del modo buffer con confirmación de todo el trabajo (para decidir cómo saber si «salió bien»). */
    suspend fun bufferTest() {
        emit("── Modo buffer ──")
        val svc = try { client.connect() } catch (e: SunmiUnavailableException) { emit("✗ ${e.message}"); return }
        step("printerInit") { client.call { printerInit(it) } }
        runCatching { svc.enterPrinterBuffer(true) }.onFailure { emit("✗ enterPrinterBuffer: ${it.message}"); return }
        emit("✓ enterPrinterBuffer(true)")
        step("texto en buffer 1") { client.call { printText("BUFFER: linea 1 (SÁBADO)\n", it) } }
        step("texto en buffer 2") { client.call { printText("BUFFER: linea 2 \$3.600\n", it) } }
        step("exitPrinterBufferWithCallback(true)") { client.call(timeoutMs = 12_000) { exitPrinterBufferWithCallback(true, it) } }
        step("lineWrap(3)") { client.call { lineWrap(3, it) } }
    }

    /** Llamadas con callback aún sin identificar (27, 29, 43): se observan sus avisos; podrían no hacer nada visible. */
    suspend fun probeUnknownCallbacks() {
        emit("── Llamadas sin identificar con callback ──")
        step("unknown27(cb)") { client.call { unknown27(it) } }
        step("unknown29(cb)") { client.call { unknown29(it) } }
        step("unknown43(cb)") { client.call { unknown43(it) } }
    }

    /** Cierre de caja de ejemplo (con pasajeros, intermedio, cargo, oferta y una anulación) por el camino real de la Fase 2. */
    suspend fun sampleReport() {
        emit("── Reporte de ejemplo (impresión directa) ──")
        val now = clock.nowMillis()
        val d = DateParts.of(now)
        fun t(id: Int, nombre: String, valor: Double, min: Int) =
            OpenTransaction(id, nombre, valor, "01-%06d".format(100 + id), d.dia, d.mes, d.ano, "%02d:%02d".format(8 + id / 6, min), now)
        val tx = listOf(
            t(1, "Público General", 3600.0, 5), t(2, "Escolar General", 2500.0, 12), t(3, "Adulto Mayor", 1800.0, 20),
            t(4, "Int. hasta 15 Km", 1800.0, 31), t(5, "Int. hasta 50 Km", 2500.0, 40), t(6, "Escolar Intermedio", 1000.0, 48),
            t(7, "Público General", 3600.0, 5), t(8, "Oferta Ruta", 6500.0, 15), t(9, "Cargo: Juan Pérez", 5000.0, 22),
            t(10, "Anulación: Público General", -3600.0, 30), t(11, "Público General", 3600.0, 41), t(12, "Cargo: María Soto", 1234567.0, 55),
        )
        val day = LocalDay.of(now)
        val report = CashReport(tx, tx.sumOf { it.valor }, ReportDate(day, 17, 25))
        val t0 = SystemClock.elapsedRealtime()
        try {
            reportPrinter.printReport(report)
            emit("✓ trabajo confirmado por la impresora en ${SystemClock.elapsedRealtime() - t0} ms (${tx.size} movimientos)")
        } catch (e: PrintFailure) {
            emit("✗ ${e.message}" + (e.code?.let { " (código $it)" } ?: "") + " · ${SystemClock.elapsedRealtime() - t0} ms")
        } catch (e: Exception) {
            emit("✗ error inesperado: ${e::class.java.simpleName}: ${e.message}")
        }
    }

    private companion object { const val TAG = "SunmiDiag" }
}
