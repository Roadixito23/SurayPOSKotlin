# Migración SurayPOSBus (Flutter) → Kotlin nativo — estado al 2026-10-21

Plan original: `/home/road/claude_plans/plan-flutter-a-kotlin.md` (y `~/.claude/plans/necesito-que-hagas-un-mossy-plum.md`).
Proyecto Kotlin: `~/projects/Suray/SurayPOSKotlin` (applicationId `cl.suray.posbus`). El proyecto Flutter (`~/projects/Suray/SurayPOSBus`) no se ha tocado.

## Cómo retomar (comandos)
```
cd ~/projects/Suray/SurayPOSKotlin
./gradlew assembleDebug                      # compila (la 1.ª vez ~3 min)
./gradlew testDebugUnitTest                  # tests JVM (~62), corren en el PC
./gradlew connectedDebugAndroidTest          # tests en el Sunmi (necesita el equipo despierto)
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
~/Android/Sdk/platform-tools/adb shell am start -n cl.suray.posbus/.MainActivity
```
- Equipo: Sunmi V1s-G, Android 6.0 (API 23), serie `VS08197Q61659`. **La pantalla se apaga sola**: antes de cada captura o test, `adb shell input keyevent KEYCODE_WAKEUP`. Si sale negro, es eso.
- Clave por defecto de la app: `123456` (login/anular) y `654321` (Ajustes).
- Los datos de prueba de la app nueva se borran con `adb shell pm clear cl.suray.posbus`.
- Si Gradle falla con "Could not read workspace metadata … metadata.bin": hay entradas de caché a medio escribir (0 bytes) en `~/.gradle/caches/8.11.1/{transforms,kotlin-dsl,dependencies-accessors}`; se borran solo esas carpetas y se reintenta (pasó una vez).
- **Git:** `SurayPOSKotlin` tiene el repositorio iniciado pero **sin ningún commit** (todo está sin versionar). Conviene hacer el primer commit.

## Decisiones tomadas
| Tema | Decisión |
|---|---|
| UI | Jetpack Compose (Material 3), una Activity + Navigation Compose, MVVM con StateFlow, DI manual (`AppContainer`) |
| Versiones | minSdk 23, targetSdk 34, compileSdk 35, AGP 8.7.3, Kotlin 2.1.0, Gradle 8.11.1, Room 2.6.1 |
| Fechas | `java.time` no existe en API 23 → clase propia `LocalDay` (`domain/Time.kt`) |
| Claves por defecto | 123456 (app) y 654321 (Ajustes); se eliminó el 232323 que Flutter usaba en algunas pantallas |
| Almacenamiento de claves | Texto plano, igual que Flutter (para importar backups). Se puede pasar a hash más adelante |
| Clave SumUp | Solo se escribe en Ajustes; no hay valor por defecto en el código ni en el APK |
| Cierre manual | Imprime → guarda en Room (crea o fusiona el cierre del día) → vacía la caja. Si falla la impresión o el guardado, la caja **no** se vacía. **Es un cambio respecto a Flutter** (allí el cierre manual no guardaba en SQLite) |
| Bloqueo por intentos | 3 libres, luego 5/15/30/60/150 s; se mide desde el fallo (Flutter reiniciaba el reloj al reabrir la pantalla) |
| Diferencia a conservar | Cierre automático clasifica sin distinguir mayúsculas; fusión/cierre manual sí distinguen (así es Flutter) |

## Hecho (todo verificado salvo lo indicado)
### Fase 0 — Toolchain
Compila e instala en el Sunmi; arranca con Compose en API 23.

### Fase 1 — Datos
Room: `daily_closings`, `transactions` (FK en cascada), `weekly_closings`, `monthly_closings` (esquema v1 de Flutter). `AppPrefs` con las mismas claves que Flutter. Correcciones: nombres de tarifa "Int. hasta 15/50 Km", precios por defecto de domingo (4300/3000/2150/3000/3000/1300), caché de feriados "fungible" por defecto. Tests en el equipo: pasaron.

### Fase 2 — Lógica de negocio (`app/src/main/kotlin/cl/suray/posbus/domain/`)
`Comprobante` (formato 01-000123, vuelve a 1 tras 999999, `rollbackLast`), `CashRegister` (caja abierta, anulación con valor negado, pendientes de días anteriores), `Closing` (clasificación y totales), `ClosingService` (cierre automático al cambiar de día + fusión, atómico), `HolidayService` (API de feriados, caché anual, renovación el 5 de enero), `Statistics`, `PendingTransactionService` (alerta cada 5 min), `NfcAuth`. Permiso `INTERNET` agregado al manifest.

### Fase 3 — Seguridad (`domain/Security.kt`)
`PasswordManager`, `LockoutPolicy`, `SumUpConfig`. Se comprobó que `sup_afk_` no aparece en ningún archivo de `SurayPOSKotlin`.
Aviso: la clave de afiliado sigue escrita en el código de **Flutter** (`sumup_service.dart`, `settings.dart`); no se tocó. Conviene rotarla en SumUp.

### Fase 4 — Pantallas (parcial)
- **Login** (`ui/login`): clave de 6 dígitos. Verificado en el equipo.
- **Home** (`ui/home`): barra superior (menú, logo, próximo comprobante, anular, reimprimir), contador ventas/anulaciones, interruptor L-S / D/F con detección automática de domingo/feriado, 6 botones de tarifa + Oferta + Cargo, fondo según tarifa, banner y diálogo de cierre pendiente, hoja de método de pago, anulación con clave, menú lateral. Verificado en el equipo con una venta y una anulación reales.
- **Reporte de caja** (`ui/report`): resumen, lista con colores por tipo, orden nuevo/antiguo, efectivo vs tarjeta, botón "Cerrar Caja e Imprimir". Verificado en el equipo: con 3 ventas ($7.900) el cierre falla por no haber impresora y la caja se conserva.

### Tests
- JVM: 62, todos pasan.
- En el equipo (Sunmi, Android 6.0): **18 pasaron**, ninguno falla. Incluyen datos, cierre automático y fusión, seguridad y el cierre manual contra Room real (guarda y vacía; el segundo cierre del día fusiona; impresión fallida no toca nada).

## Lo que falta
### Pantallas (fase 4)
1. **Ajustes** (en Flutter `settings.dart`, ~3100 líneas; hacerlo por secciones): tarifas L-S y domingo, claves (app, ajustes, comprobante), tarjetas NFC vinculadas, activar SumUp + escribir la clave de afiliado, margen del PDF (`pdfEndMargin`), N° de comprobante, abreviaturas de tarifas para el PDF. **Sin esto no hay dónde cambiar tarifas ni la clave de SumUp.**
2. **Cargo** (`cargo_screen`) y **Oferta** (diálogo múltiple de `home.dart`): hoy los botones solo muestran "pendiente de portar".
3. **Recuperación de reportes** (`reporte_recovery`, 789 líneas) — el botón "Anteriores" del reporte ya apunta ahí.
4. **Estadísticas** (la lógica ya está en `domain/Statistics.kt`; falta la pantalla y los gráficos).
5. **Backup** (`.suray`, ZIP con preferencias, `cargo_receipts/`, `pdfs/`; verificar si incluye la DB).
6. **Transferencia** (QR de WhatsApp y banco; assets `qr_codes/`).
7. **Seguridad** (pantalla de bloqueo con vibración) y **Colores de botones**.
8. Pequeños pendientes de Login/Home: acceso por NFC, barra de título "Inicio de Sesión", verificar el diálogo de anular con el teclado abierto, ancho del menú lateral (ya acotado a 300 dp, sin ver en pantalla), íconos personalizables de botones.

### Integraciones (fase 5)
- **Impresión** (lo más riesgoso): hoy `UnavailableTicketPrinter` y `UnavailableReportPrinter` lanzan error a propósito. Hay que implementar tickets (normal, oferta/MO, cargo con copia cliente y cargo) y reporte en PDF 58 mm con `PdfDocument` + `PrintManager` (Flutter usa `Printing.layoutPdf`). Recursos: `logobkwt`, `headTicket`, `endTicket`, `tijera`. Opcional después: servicio de impresora Sunmi para imprimir sin diálogo. Las interfaces `TicketPrinter` / `ReportPrinter` ya están conectadas a Home y Reporte.
- **Reimpresión** desde Home (hoy el botón no hace nada).
- **NFC** (`NfcAdapter.enableReaderMode`), login y clave por tarjeta; la lógica ya está en `NfcAuth`.
- **SumUp**: intent `sumupmerchant://pay/1.0` (`affiliate-key`, `app-id=posbus`, `amount`, `currency=CLP`, `title`, `callback=posbus://sumup-result`), recibir el resultado en `onNewIntent`/`onCreate` con intent-filter. No usar `<queries>` (rompe el parseo en Sunmi) ni `canLaunchUrl`: lanzar y capturar `ActivityNotFoundException`. Guardar pago con `CardPayments` (ya implementado).
- **Backup**: permisos de almacenamiento en tiempo de ejecución en API 23 y `FileProvider` para compartir.

### Migración y release (fase 6)
- Importar preferencias/DB/`.suray` de la app Flutter (misma clave SumUp incluida).
- Release: R8, `abiFilters armeabi-v7a`, keystore propio (Flutter firma con la clave debug), `versionName` con formato de fecha como hoy.
- Medir rendimiento y RAM en el V1s con Compose.

## Riesgos y cosas a recordar
- La impresión depende del hardware y todavía no existe: sin ella el equipo no puede emitir tickets ni cerrar la caja.
- Ventas de días mezclados en un cierre manual se guardan bajo la fecha del reporte (la de la primera venta si todas son del mismo día, si no la fecha actual); no se ha comparado con Flutter.
- Cuando el equipo se conecte de nuevo: comprobar que la app Flutter y la nueva conviven (distinto applicationId).
- Orden sugerido para continuar: Ajustes → impresión (tickets + reporte) → Oferta/Cargo → SumUp → NFC → Recuperación/Estadísticas → Backup → Transferencia/Seguridad/Colores → migración de datos.
