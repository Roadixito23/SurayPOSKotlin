# Interfaz binaria del servicio de impresión Sunmi (V1s, servicio 4.15.2)

Fase 0 del plan de impresión directa · 2026-10-22 · método: análisis estático del APK real del equipo
(`/system/app/SunmiPrinterService_v4.15.2_V1s_sunmi_release`, extraído con `adb pull`, leído con `dexdump`; **no se ejecutó nada**).

## Resumen
- El servicio es `woyou.aidlservice.jiuiv5` (acción `woyou.aidlservice.jiuiv5.IWoyouService`). Su código está **ofuscado** (la interfaz es `k.a.a.l`, el callback `k.a.a.c`), pero el contrato binario no cambia con la ofuscación: lo que cuenta es el **descriptor** (`woyou.aidlservice.jiuiv5.IWoyouService` / `…ICallback`), el **orden de las transacciones** y el **formato de cada parámetro**.
- Hay **47 transacciones** en `IWoyouService` y **4** en `ICallback`. Las formas de parámetros de las que usaremos coinciden con el AIDL clásico público de Sunmi.
- Consecuencia para el código: se escribe nuestro propio `IWoyouService.aidl` (paquete `woyou.aidlservice.jiuiv5`) declarando **los 47 métodos en este mismo orden**; los que no usamos pueden llevar un nombre provisional porque nunca se invocan. Si el orden se altera, las llamadas se cruzan.
- Conectarse no requiere permisos (el único permiso propio, `com.sunmi.permission.OPEN_CASH`, es del cajón y de firma).

## `ICallback` (4 transacciones, orden exacto)
| Código | Firma | Nombre |
|---|---|---|
| 1 | `(boolean)` | `onRunResult(isSuccess)` — el comando se ejecutó |
| 2 | `(String)` | `onReturnString(result)` — respuesta de texto |
| 3 | `(int, String)` | `onRaiseException(code, msg)` — error |
| 4 | `(int, String)` | `onPrintResult(code, msg)` — resultado del trabajo (modo buffer) |

Coincide con el orden del AIDL oficial. `onPrintResult` es la pieza para saber si un trabajo completo terminó bien.

## `IWoyouService` — 47 transacciones observadas
Columnas: código de transacción · firma Java observada · parámetros que se escriben en el `Parcel` · valor que se lee de vuelta · nombre (según el AIDL oficial; «sin identificar» = por determinar en la Fase 1).
Notación: `cb` = `ICallback` (se escribe como `StrongBinder`).

| Cód. | Firma observada | Escribe | Lee | Nombre |
|---|---|---|---|---|
| 1 | `(Ljava/lang/String;[BII)Z` | String,ByteArray,Int,Int | Exception,Int | sin identificar |
| 2 | `()I` | — | Exception,Int | getFirmwareStatus (?) |
| 3 | `()Ljava/lang/String;` | — | Exception,String | getServiceVersion |
| 4 | `(Lk/a/a/c;)V` | StrongBinder | Exception | printerInit |
| 5 | `(Lk/a/a/c;)V` | StrongBinder | Exception | printerSelfChecking |
| 6 | `()Ljava/lang/String;` | — | Exception,String | getPrinterSerialNo |
| 7 | `()Ljava/lang/String;` | — | Exception,String | getPrinterModal |
| 8 | `()Ljava/lang/String;` | — | Exception,String | getPrinterVersion |
| 9 | `(Lk/a/a/c;)V` | StrongBinder | Exception | getPrintedLength |
| 10 | `(ILk/a/a/c;)V` | Int,StrongBinder | Exception | lineWrap(n) |
| 11 | `([BLk/a/a/c;)V` | ByteArray,StrongBinder | Exception | sendRAWData(bytes) |
| 12 | `(ILk/a/a/c;)V` | Int,StrongBinder | Exception | setAlignment(0/1/2) |
| 13 | `(Ljava/lang/String;Lk/a/a/c;)V` | String,StrongBinder | Exception | setFontName |
| 14 | `(FLk/a/a/c;)V` | Float,StrongBinder | Exception | setFontSize(float) |
| 15 | `(Ljava/lang/String;Lk/a/a/c;)V` | String,StrongBinder | Exception | printText |
| 16 | `(Ljava/lang/String;Ljava/lang/String;FLk/a/a/c;)V` | String,String,Float,StrongBinder | Exception | printTextWithFont |
| 17 | `([Ljava/lang/String;[I[ILk/a/a/c;)V` | StringArray,IntArray,IntArray,StrongBinder | Exception | printColumnsText(textos, anchos, alineaciones) |
| 18 | `(Landroid/graphics/Bitmap;Lk/a/a/c;)V` | Int,Int,StrongBinder | Exception | printBitmap |
| 19 | `(Ljava/lang/String;IIIILk/a/a/c;)V` | String,Int,Int,Int,Int,StrongBinder | Exception | printBarCode |
| 20 | `(Ljava/lang/String;IILk/a/a/c;)V` | String,Int,Int,StrongBinder | Exception | printQRCode |
| 21 | `(Ljava/lang/String;Lk/a/a/c;)V` | String,StrongBinder | Exception | printOriginalText |
| 22 | `([Lcom/sunmi/trans/TransBean;Lk/a/a/c;)V` | TypedArray,StrongBinder | Exception | commitPrint(TransBean[]) |
| 23 | `()V` | — | Exception | commitPrinterBuffer |
| 24 | `(Z)V` | Int | Exception | enterPrinterBuffer(limpiar) |
| 25 | `(Z)V` | Int | Exception | exitPrinterBuffer(confirmar) |
| 26 | `([BLk/a/a/h;)V` | ByteArray,StrongBinder | Exception | sin identificar |
| 27 | `(Lk/a/a/c;)V` | StrongBinder | Exception | sin identificar |
| 28 | `()V` | — | Exception | sin identificar |
| 29 | `(Lk/a/a/c;)V` | StrongBinder | Exception | sin identificar |
| 30 | `(ZLk/a/a/c;)V` | Int,StrongBinder | Exception | sin identificar |
| 31 | `([Ljava/lang/String;[I[ILk/a/a/c;)V` | StringArray,IntArray,IntArray,StrongBinder | Exception | printColumnsString(textos, anchos, alineaciones) |
| 32 | `()I` | — | Exception,Int | sin identificar |
| 33 | `(Landroid/graphics/Bitmap;ILk/a/a/c;)V` | Int,Int,Int,StrongBinder | Exception | printBitmapCustom(bitmap, tipo) |
| 34 | `()I` | — | Exception,Int | sin identificar |
| 35 | `()Z` | — | Exception,Int | sin identificar |
| 36 | `()Z` | — | Exception,Int | sin identificar |
| 37 | `()Z` | — | Exception,Int | sin identificar |
| 38 | `()I` | — | Exception,Int | sin identificar |
| 39 | `()I` | — | Exception,Int | sin identificar |
| 40 | `()I` | — | Exception,Int | sin identificar |
| 41 | `(Ljava/lang/String;IIILk/a/a/c;)V` | String,Int,Int,Int,StrongBinder | Exception | sin identificar |
| 42 | `()I` | — | Exception,Int | sin identificar |
| 43 | `(Lk/a/a/c;)V` | StrongBinder | Exception | sin identificar |
| 44 | `(II)V` | Int,Int | Exception | sin identificar |
| 45 | `()I` | — | Exception,Int | sin identificar |
| 46 | `()V` | — | Exception | sin identificar |
| 47 | `()V` | — | Exception | sin identificar |

> Los códigos 26 y 27–30, 32, 34–47 no se usarán en el reporte (cajón, LCD, cortador, estado extendido…). Los métodos `()I` y `()Z` de 32–45 incluyen el **estado de la impresora** (papel, tapa, sobrecalentamiento): el que corresponda a `updatePrinterState` se identifica **empíricamente en la Fase 1** llamándolos con y sin papel, porque el nombre no se puede leer en el código ofuscado.

## Lo que se usará para el reporte
`printerInit` (4) · `setAlignment` (12) · `setFontSize` (14) · `printText` (15) · `printTextWithFont` (16) · `printColumnsText` (17) o `printColumnsString` (31) · `lineWrap` (10) · `sendRAWData` (11, ESC/POS: negrita, texto invertido) · `enterPrinterBuffer` (24) / `commitPrinterBuffer` (23) / `exitPrinterBuffer` (25) · `getPrinterSerialNo`/`getPrinterModal`/`getServiceVersion` (6/7/3) para diagnóstico.

## Hallazgos de la Fase 1 (medidos en el V1s real)
- **Conexión:** `bindService` con acción `woyou.aidlservice.jiuiv5.IWoyouService` y paquete `woyou.aidlservice.jiuiv5` conecta en milisegundos, sin permisos.
- **Códigos 7 y 8 estaban intercambiados** respecto al AIDL clásico: el 7 devuelve la **versión del firmware** (`1.24`) y el 8 el **modelo** (`POS-V1S`). El nº de serie (6) viene como texto hexadecimal.
- **Las consultas responden por `onReturnString`**, no por `onRunResult` (p. ej. `getPrintedLength` → `string=1126`, sin `onRunResult`). El cliente las da por terminadas con `onReturnString`.
- **Los comandos de impresión (texto, alineación, tamaño, RAW, columnas, saltos) responden `onRunResult(true)` en ~1-50 ms**: significa «comando aceptado en la cola», **no** «ya salió en papel».
- **Estado de la impresora = código 32** (`updatePrinterState`): **1 = normal, 4 = sin papel / tapa abierta** (medido en el V1s al abrir la tapa). `unknown42` queda en 1 con y sin papel: no es el estado. El resto (`unknown2=0, 34=0, 35/36/37=false, 38=-1, 39=0, 40=100, 45=-1`) no cambió.
- **Avisos por broadcast** (el servicio los emite al cambiar el estado; ojo al nombre con `jiuv5` sin «i» y a la errata `ACITON`): `woyou.aidlservice.jiuv5.` + `NORMAL_ACTION`, `OUT_OF_PAPER_ACTION`, `LESS_OF_PAPER_ACTION`, `COVER_OPEN_ACTION`, `COVER_ERROR_ACTION`, `OVER_HEATING_ACITON`, `NORMAL_HEATING_ACITON`, `MOTOR_HEATING_ACITON`, `PAPER_ERROR_ACITON`, `ERROR_ACTION`, `INIT_ACTION`, `FIRMWARE_*`, `KNIFE_ERROR_ACTION_1/2`, `PRINTER_NON_EXISTENT_ACITON`. El laboratorio los escucha y los registra.

### Modo buffer (probado)
- Secuencia: `printerInit` → `enterPrinterBuffer(true)` → comandos (`printText`, columnas, RAW…) → **`exitPrinterBufferWithCallback(true, cb)`** (código 30).
- **Dentro del buffer los comandos no responden** (no llega `onRunResult`; un `call` normal da «sin respuesta» tras el tiempo de espera): se encolan y hay que **escribirlos sin esperar callback**. Solo el cierre responde.
- Al cerrar con commit llega **`onPrintResult(0, "Transaction print successful!")`** (~250 ms con 2 líneas): es la confirmación a nivel de trabajo que necesitábamos. Código 0 = éxito.
- **Pendiente:** qué código/mensaje devuelve `onPrintResult` con la tapa abierta o sin papel, y si el trabajo queda en cola y sale solo al cerrar la tapa (riesgo de duplicados al reintentar).
- Llamadas sin identificar con callback: 27 → `onReturnString("")`; 29 → nunca responde; 43 → `onRunResult(true)` sin efecto visible. No se usan.

## Qué **no** pudo resolverse por análisis estático (pasa a la Fase 1)
1. **Cuál de los `()I` es el estado de la impresora** y qué valores devuelve cada situación (normal, sin papel, tapa abierta, sobrecalentada).
2. **Diferencia entre `printColumnsText` (17) y `printColumnsString` (31):** ambos reciben `String[]`, `int[]`, `int[]`; falta confirmar la unidad del ancho (caracteres o proporción) y los códigos de alineación.
3. **Codificación de `printText`** (tildes, ñ, `$`): se prueba imprimiendo `SÁBADO`, `Aysén`, `Gestión`, `$3.600`.
4. **Ancho útil real** en caracteres a cada tamaño de fuente (se espera ~32 columnas a 24 px en 58 mm / 384 puntos).
5. **Qué devuelve `onPrintResult` con papel agotado / tapa abierta** (decide la fiabilidad de «éxito»). *(El caso feliz ya está medido; falta el de error.)*
6. **Transacción 1** `(String, byte[], int, int) → boolean`: función no identificada; no se usa.

## Fuentes
- Servicio instalado en el propio Sunmi V1s (versión 4.15.2, código 211104001).
- Documentación Sunmi: [Sunmi Built-in Printer Developer Documentation (PDF)](https://file.cdn.sunmi.com/SUNMIDOCS/%E5%95%86%E7%B1%B3%E5%86%85%E7%BD%AE%E6%89%93%E5%8D%B0%E6%9C%BA%E5%BC%80%E5%8F%91%E8%80%85%E6%96%87%E6%A1%A3EN-0224.pdf) (existe, pero su texto no se pudo leer en este entorno por usar fuentes codificadas; no se usó como fuente de datos).
- Confirmación de que el servicio `woyou.aidlservice.jiuiv5` es el de la familia V1/V2/P1/P2/T1/T2: [pub.dev · api_pos_printer](https://pub.dev/packages/api_pos_printer).
