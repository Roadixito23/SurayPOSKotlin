# Plan: correcciones de caja (anulación, atomicidad del cierre, doble toque) — 2026-11-25

Origen: análisis del 2026-11-25. Se corrigen los puntos 1, 3 y 4; el punto 2 (reuso del comprobante al anular) **no se toca** (comportamiento heredado de Flutter, queda a decisión del usuario).

## Reparto (agentes en paralelo, archivos disjuntos)
| Agente | Punto | Archivos que puede tocar |
|---|---|---|
| A | 1. Doble anulación tras reiniciar | `domain/CashRegister.kt`, la línea `canCancel = …` del `combine` `base` en `ui/home/HomeViewModel.kt`, tests JVM nuevos/existentes de la caja |
| B | 3. Cierre no atómico + `apply()` | `domain/ClosingService.kt`, `data/prefs/AppPrefs.kt`, `data/db/Daos.kt` si hace falta, tests JVM/androidTest de cierre |
| C | 4. Doble toque en venta | solo `launchSale` en `ui/home/HomeViewModel.kt` |

Ningún agente ejecuta Gradle (límite de RAM del PC). El coordinador compila y corre `testDebugUnitTest` al final.

## 1. Doble anulación (agente A)
- Problema: `cancelLast()` siempre toma la última venta no anulación, aunque ya esté anulada; el único freno (`hasCancelled`) vive en memoria.
- Solución: derivarlo de los datos guardados. La venta candidata es la última no anulación (por `id`); **solo es anulable si no existe una línea de anulación con `id` mayor**. (No se compara por comprobante porque al anular el contador retrocede y la venta siguiente reutiliza el número.)
- `CashRegister.canCancelLast(): Boolean`; `cancelLast()` devuelve `false` si no se puede. Home usa `canCancel = c.register.canCancelLast()` (o la regla equivalente sobre `open`). `hasCancelled` puede quedarse como freno adicional.
- Tests: anular dos veces seguidas → la segunda devuelve false; recrear `CashRegister` desde el mismo store (simula reinicio) → sigue sin poder; venta nueva tras anular → se puede anular.

## 3. Cierre no atómico (agente B)
- Problema: `performAutoClose` / cierre manual guardan en Room y vacían la caja (SharedPreferences) después; si el proceso muere entre ambos, el siguiente cierre vuelve a insertar las mismas ventas. Además `AppPrefs` usa `apply()` (asíncrono) para datos de caja.
- Solución:
  - Hacer `merge` idempotente: descartar de `newTransactions` las que ya existen en el cierre (misma huella: comprobante + nombre + valor + timestamp/hora, según lo que guarde la entidad).
  - `commit()` en los setters críticos de `AppPrefs`: transacciones abiertas, `openNextId`, `comprobanteNumber`, `ticketId`, última venta, `lastTransactionDate`.
- Tests: fusionar dos veces la misma lista no duplica filas ni totales (androidTest si requiere Room real; JVM si se puede con fakes).

## 4. Doble toque (agente C)
- Problema: `launchSale` comprueba `busy` antes de `launch`, pero lo activa después de `checkAndAutoClose()` (que puede suspender en Room en el primer toque del día).
- Solución: activar `busy = true` de forma síncrona antes de `launch` y liberarlo en `finally`, cubriendo también la salida temprana por pendientes y el error al guardar.

## Verificación
`./gradlew testDebugUnitTest` y `assembleDebug` en verde; revisión del diff. Pruebas en el Sunmi (`connectedDebugAndroidTest`) cuando el equipo esté conectado.
