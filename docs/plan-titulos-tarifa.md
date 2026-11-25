# Plan: títulos de tarifa editables — 2026-11-25

Rama: `feat/tarifa-unica` (worktree `SurayPOSKotlin-tarifa`), sobre los commits 5e348de y 0655d40 que ya implementan la tarifa única.

## Situación actual
- Antes: dos tarifas, «Lunes a Sábado» y «Domingo/Feriado», con interruptor L-S / D/F en Inicio.
- Ahora: «Tarifa Única» (activa por defecto, `AppPrefs.unifiedFare`).
- Los títulos están **fijos en el código**: `domain/FareMode.kt` → `FareKind(ticketTitle)` = `TARIFA ÚNICA` / `TARIFA LUNES A SÁBADO` / `TARIFA DOMINGO/FERIADO`. Además hay textos sueltos: «Precios (toda la semana)», «Lunes a sábado», «Domingo y feriados» (Ajustes y Colores), «L-S» / «D/F» (Inicio).

## Objetivo
Que el nombre de cada tarifa sea un dato editable en Ajustes y se use en todas partes: ticket, reimpresión, Ajustes → Precios, Colores de botones e Inicio.

## Diseño
1. **Modelo** (`domain/FareMode.kt`)
   - `data class FareTitle(val name: String, val short: String)` (nombre completo para el ticket y Ajustes; abreviatura para el interruptor de Inicio).
   - Valores por defecto en `FareKind.defaultTitle`: Única → «Tarifa Única» / «Única»; L-S → «Lunes a Sábado» / «L-S»; D/F → «Domingo/Feriado» / «D/F».
   - `FareTitles.validate(name, short)`: `trim`, no vacío, nombre ≤ 24 caracteres (el ticket imprime `"TARIFA " + …` o el nombre tal cual en 58 mm / 32 columnas), abreviatura ≤ 4. Devuelve `Ok`/`Invalid(motivo)`.
   - Título del ticket = `name` tal cual.
2. **Persistencia** (`AppPrefs`)
   - Claves nuevas `fare_title_unified`, `fare_title_weekday`, `fare_title_sunday` (+ `_short`), con `commit()` no necesario (no es dato de caja).
   - Sin clave → valor por defecto: instalaciones existentes y copias de Flutter no cambian.
   - Entran solas en el backup `.suray` (el backup copia el mapa de preferencias); comprobar con un test de `BackupTest`.
3. **Ticket y reimpresión**
   - `PrintLayouts`/`TicketPrinter` reciben el título resuelto (string), no el enum.
   - `LastTransaction.fareTitle: String?` guarda el título **impreso** en la venta: la reimpresión usa el original aunque después se renombre la tarifa. Ventas antiguas: `fareKind`/`switchValue` → título actual.
4. **Ajustes → Precios**
   - Cada tarjeta de precios muestra su nombre editable (lápiz → ventana flotante `SurayDialog`, como las claves y tarifas): campos nombre y abreviatura, «Restaurar nombre», validación en vivo.
   - El encabezado de la tarjeta usa el nombre: con tarifa única «Tarifa Única», si no «Lunes a Sábado» y «Domingo/Feriado».
5. **Resto de la UI**: `ButtonColorsScreen` (grupos), `HomeScreen` (interruptor: abreviaturas), mensajes que nombren la tarifa → todo desde `FareTitles` vía `AppContainer`; refresco por `settingsRevision`.

## Fuera de alcance
- Agregar más de tres tarifas o horarios por tarifa (sería un modelo de tarifas dinámico; plan aparte si se quiere).
- Cambiar precios por defecto o la detección de feriados.

## Decisiones (usuario, 2026-11-25)
- El ticket imprime el nombre **tal cual lo escribe el usuario** (sin pasar a mayúsculas ni anteponer «TARIFA»). Por eso los nombres de fábrica son los textos actuales del ticket: «TARIFA ÚNICA», «TARIFA LUNES A SÁBADO», «TARIFA DOMINGO/FERIADO» (el ticket no cambia mientras no se edite).
- La abreviatura del interruptor (L-S / D/F) **también es editable** (máx. 4).
- Largo máximo del nombre: **24**.
- Commits al terminar.

## Base ya implementada (coordinador)
`FareTitle(name, short)`, `FareKind.defaultTitle`, `FareTitles.validate` (`domain/FareMode.kt`); `AppPrefs.fareTitle(kind)` / `setFareTitle(kind, title?)` (claves `fare_title_*`, `fare_short_*`; entran en el backup porque `snapshot()` copia todas las preferencias); `LastTransaction.fareTitle`.

## Reparto (agentes en paralelo, archivos disjuntos)
| Agente | Alcance |
|---|---|
| Coordinador | base (arriba) + tests JVM de `FareTitles` y de las preferencias |
| A Impresión | `PrintLayouts.kt`, `TicketPrinter.kt`, `AndroidPrinter.kt`, `HomeViewModel` (guardar/usar `fareTitle`), `PrintLayoutsTest` |
| B UI | `SettingsScreen/ViewModel` (editor), `ButtonColorsScreen`, `HomeScreen` |
Interfaz acordada antes de lanzar: `AppPrefs.fareTitle(kind): FareTitle`, `AppPrefs.setFareTitle(kind, FareTitle?)` (null = restaurar), `FareTitles.validate(...)`.

## Verificación
`./gradlew testDebugUnitTest assembleDebug`; en el Sunmi: renombrar la tarifa, vender, reimprimir tras volver a renombrar (debe salir el título original), crear y restaurar un backup.

## Nota de integración
Las correcciones de caja (`docs/plan-correcciones-caja.md`, sin commit en `feat/ui-claves`) tocan `HomeViewModel.launchSale` y `AppPrefs`; esta rama también los toca. Conviene hacer commit de aquellas primero y rebasar `feat/tarifa-unica` encima antes de empezar.
