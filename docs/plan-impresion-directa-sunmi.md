# Plan: impresión directa Sunmi para el cierre de caja

Estado: **Fases 0 y 1 completadas; Fase 2 con código listo, pendiente de validar en papel** — 2026-10-22. Documento vivo: se actualiza al cerrar cada fase.

## Objetivo
Probar si el **cierre de caja** puede imprimirse conectándose directo al servicio de impresión del Sunmi V1s
(`woyou.aidlservice.jiuiv5`, `IWoyouService`) en vez del camino heredado de Flutter
(PDF → `PrintManager` → plugin del sistema con diálogo). Si funciona, migrar el resto de tickets después.

Alcance inicial: **solo el cierre de caja**. Tickets, oferta y cargo no se tocan hasta la Fase 5.

## Decisiones tomadas (usuario)
| Tema | Decisión |
|---|---|
| Formato | **Texto y columnas nativas** (no imagen): es un cierre, interesa nitidez y velocidad |
| Confirmación | **Alertbox tras imprimir** «¿Salió bien? — Cerrar caja / Reimprimir / Cancelar», como antes con el diálogo del sistema. La caja solo se vacía al confirmar |
| Error del servicio | **Mostrar error y permitir reintentar**; sin volver automáticamente al PDF en la primera versión |
| PDF | **Se sigue guardando el PDF** de cada cierre (sin imprimirlo) para «Recuperación de reportes» y el backup |
| Flota | Solo **Sunmi V1s** (Android 6, servicio de impresión 4.15.2) |
| Paquete `com.busessuray.sunmi_bluetooth_server_new` | El usuario no lo conoce. No se usa ni se toca; se anota como curiosidad (instalado 2025-12-10, targetSdk 36) |

## Lo que ya se sabe del equipo
- Servicio `woyou.aidlservice.jiuiv5` v4.15.2 (versionCode 211104001), app de sistema, acciones: `woyou.aidlservice.jiuiv5.IWoyouService`, `.APPEND_PRINT`, `.SUBSCRIBE_PRINT_DATA`.
- Conectarse no requiere permisos (solo `com.sunmi.permission.OPEN_CASH`, del cajón, es de firma).
- Hoy se imprime con `com.sunmi.printerplugin` 1.1.1 vía el diálogo de Android.

## Fases
0. ✅ **Reconocimiento (sin tocar la app) — HECHO:** verificar la interfaz AIDL real del servicio instalado (orden de métodos, callbacks, estado del papel). Resultado en `docs/sunmi-aidl-4.15.2.md`: 47 transacciones de `IWoyouService` y 4 de `ICallback` leídas del APK real; las que usaremos (init, alineación, fuente, texto, columnas, líneas, RAW, buffer) están identificadas y coinciden con el AIDL clásico. Quedan 6 incógnitas para la Fase 1 (método de estado, unidad de las columnas, codificación de tildes, ancho útil, `onPrintResult` con papel agotado).
1. ✅ **Prueba de concepto — HECHA:** Ajustes → Impresión → «Probar impresora» (conexión, estado, página de prueba con tildes, ñ, `$`, columnas, negrita, alineación). Criterio de salida: todo sale correcto y el resultado del trabajo es fiable.
2. 🔄 **Reporte por impresión directa (EN CURSO — código listo, falta validar el papel):** modelo de líneas de recibo (puro, testeable) generado desde `ReportLayout`; `SunmiDirectPrinter` que implementa `ReportPrinter`; tests JVM + impresor falso.
3. **Integración y seguridad del cierre:** preferencia «Método de impresión», revisión del estado antes de imprimir, alertbox de confirmación, regla existente (si falla, la caja no se vacía), copia PDF silenciosa.
4. **Prueba comparativa en el equipo:** mismo cierre por ambos métodos; tiempo, largo de papel, legibilidad, 50 impresiones seguidas, reporte de 300 filas, papel agotado, tapa abierta, servicio detenido, pantalla apagada.
5. **Decisión:** si funciona, extender a tickets/oferta/cargo (logo y QR como imagen) y reimpresión; el PDF queda solo como archivo. Si no, queda como opción experimental o se descarta sin haber roto nada.

## Riesgos
1. Sin vista previa: el servicio puede responder «ok» aunque el papel se agote a mitad → mitigado con estado previo + alertbox de confirmación.
2. Codificación de caracteres (tildes/ñ) → se prueba en la Fase 1.
3. AIDL que no coincide con el servicio instalado (llamadas cruzadas) → se verifica en la Fase 0 contra el APK real.
4. Sin simulador: lo probable sin hardware es el renderizado y el flujo con un impresor falso; el resto gasta papel.

## Registro
- 2026-10-22: **Fase 2 (código):** `domain/Receipt.kt` (modelo de líneas, 32 columnas, `PrinterState`), `domain/ReceiptPrinting.kt` (`ReceiptDevice` + `ReceiptEngine`: comprueba el estado ANTES, un solo trabajo en buffer, éxito solo con la confirmación del servicio, descarta si algo falla), `print/sunmi/SunmiReceiptDevice` (adaptador), `SunmiReportPrinter : ReportPrinter`. 16 tests JVM nuevos (líneas ≤ 32 columnas, contenido idéntico al PDF, truncado de celdas, motor con dispositivo falso: estado no listo, fallo al enviar, confirmación fallida). Botón «Reporte de ejemplo» en el laboratorio: 12 movimientos → **trabajo confirmado en 5,9 s**. Falta que el usuario compare el papel.
- 2026-10-22: **Fase 1 cerrada.** El usuario confirmó en papel todas las pruebas (texto, columnas, ESC/POS, buffer, errores). **Estado de la impresora = llamada 32** (`updatePrinterState`): 1 normal, 4 sin papel/tapa abierta; `unknown42` se queda en 1 (no es el estado). El servicio emite `OUT_OF_PAPER_ACTION` / `NORMAL_ACTION` al abrir/cerrar la tapa. Pendiente de capturar: código exacto de `onPrintResult` ante fallo (el motor lo muestra tal cual y se registrará en la Fase 4).
- 2026-10-22: **Papel verificado por el usuario: todo bien** (tildes, ñ, `$`, ancho, alineación, tamaños, ESC/POS y columnas). **Modo buffer probado:** `exitPrinterBufferWithCallback(true)` devuelve `onPrintResult(0, "Transaction print successful!")` = confirmación de trabajo completo; dentro del buffer los comandos no responden. Pendiente de Fase 1: comportamiento con tapa abierta / sin papel (acción física del usuario).
- 2026-10-22: **Fase 1 en curso.** Laboratorio listo en Ajustes → Impresión (AIDL propio de 47 métodos, cliente `SunmiPrinterClient`, `SunmiDiagnostics`). Medido: conexión OK; consultas OK; página de prueba enviada con 60/60 comandos aceptados. **Falta la verificación en papel** (tildes, ancho, alineación, tamaños, ESC/POS, columnas) y la lectura de estado con tapa abierta / sin papel.
- 2026-10-22: **Fase 0 completada.** El contrato binario es verificable y estable: se escribirá un `IWoyouService.aidl` propio con los 47 métodos en el orden observado. Hallazgo: el servicio está ofuscado, pero el orden de transacciones es lo único que importa y ya está documentado. Pendiente para la Fase 1: identificar empíricamente el método de estado de la impresora.
- 2026-10-22: plan aprobado con las decisiones de arriba; inicio de la Fase 0.
