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
- JVM: 94, todos pasan (`SettingsInputTest`, `PrintLayoutsTest`).
- En el equipo (Sunmi, Android 6.0): **18 pasaron**, ninguno falla. Incluyen datos, cierre automático y fusión, seguridad y el cierre manual contra Room real (guarda y vacía; el segundo cierre del día fusiona; impresión fallida no toca nada).

## Lo que falta
### Pantallas (fase 4)
1. ~~**Ajustes**~~ **Hecho (2026-10-22)**, `ui/settings`: protegido con la clave de Ajustes; pestañas General (ID de terminal, reinicio del contador con clave del comprobante, cambio de las 3 claves, SumUp + clave de afiliado, lista/quitar tarjetas NFC), Precios (editar/restaurar L-S y domingo), Apariencia (íconos, opacidad, tamaño de texto, margen PDF) y Abreviaturas. Los cambios llegan a Home vía `AppContainer.settingsRevision`. Verificado en el equipo: acceso con clave, edición de tarifa persistida. **Falta:** vincular tarjetas NFC nuevas (con la fase NFC), íconos personalizables por botón, y que Home use `buttonOpacity`. Margen PDF por defecto corregido a 69 (antes 0; Flutter usa 69).
2. ~~**Cargo** y **Oferta**~~ **Hecho (2026-10-22)**: `ui/cargo/CargoScreen.kt` (destino Aysén/Coyhaique, artículo, precio, destinatario, teléfono opcional de 9 dígitos), `ui/home/OfferDialog.kt` (líneas cantidad × valor, total en vivo). Ambas ventas pasan por la misma canalización que el efectivo (`HomeViewModel.launchSale`: cierre automático → comprobante → caja → última venta → impresión; un fallo de impresión no revierte la venta). Oferta = 1 comprobante y 1 movimiento «Oferta Ruta»; cargo = 1 comprobante, movimiento «Cargo: destinatario» y 2 tickets (copia cliente y copia carga con cinta, tijera, control interno y recuadro de entrega). Reimpresión: oferta una vez; cargo ilimitada con elección Cliente/Carga/Ambas. Verificado en el equipo: oferta de 2 líneas, cargo completo y reimpresión de la copia carga. Diferencias con Flutter: la hora del control interno es la real (Flutter ponía «13:31» fija); oferta exige cantidad y valor > 0 (Flutter permitía total 0); el cargo se guarda antes de imprimir (Flutter lo guardaba después). **Pendiente de comprobar en papel:** la vista previa del Sunmi reduce la copia carga (830 pt de alto) para que quepa en pantalla; no se ha impreso físicamente.
3. ~~**Recuperación de reportes**~~ **Hecho (2026-10-22)**, `ui/recovery` + `domain/ReportFiles.kt`: lista los PDF de `files/pdfs/` (más reciente primero), búsqueda por nombre, estadísticas (total, últimos 7 días, espacio) y reimpresión con `PdfFilePrinter` (el alto de página se lee con `PdfRenderer`). Verificado en el equipo con un cierre real. Alcance = el de Flutter (listar/buscar/reimprimir); no hay borrado. El menú y el botón «Anteriores» del reporte llevan aquí.
4. ~~**Estadísticas**~~ **Hecho (2026-10-22)**, `ui/stats`: resumen de 30 días (total, transacciones, promedio diario, días activos), gráfico de barras de la semana (hecho con Compose, sin librería), ranking de los 6 pasajes más vendidos y datos destacados (mejor día, más vendido, promedios semanal/Lun–Sáb/domingos); botón actualizar. Lee las ventas cerradas (Room) + la caja abierta. Verificado en el equipo con 5 ventas; las cifras cuadran.
5. ~~**Backup**~~ **Hecho (2026-10-22)**, `data/backup` + `ui/backup`: `.suray` = ZIP con `preferences.json` (mapa plano con marcadores `clave_type`, igual que Flutter), `cargo_receipts/`, `pdfs/` y, como extra, `database/suraypos.db` (el backup de Flutter **no incluye SQLite**: los cierres históricos de Flutter no viajan en sus copias). Crear, listar, compartir (FileProvider), exportar a archivo (selector del sistema, sin permisos de almacenamiento), **importar** un `.suray` externo (sirve para traer copias de Flutter) y restaurar. Restaurar acepta ambas variantes, valida todo antes de tocar nada (ZIP, JSON, cabecera SQLite), ignora rutas con `..`/absolutas/subcarpetas y escribe cada tipo con el que lee `AppPrefs` (Int/Long/Float). Tras restaurar hay que reiniciar la app (botón «Cerrar la aplicación»). **La clave de afiliado de SumUp no se incluye en las copias** (las claves de la app sí van en texto plano, como en Flutter). Verificado en el equipo: crear → venta → restaurar vuelve al estado de la copia y la base restaurada abre. **No probado:** compartir y exportar a archivo (diálogos del sistema), importar desde el selector.
6. **Transferencia** (QR de WhatsApp y banco; assets `qr_codes/`).
7. **Seguridad** (pantalla de bloqueo con vibración) y **Colores de botones**.
8. Pequeños pendientes de Login/Home: acceso por NFC, barra de título "Inicio de Sesión", verificar el diálogo de anular con el teclado abierto, ancho del menú lateral (ya acotado a 300 dp, sin ver en pantalla), íconos personalizables de botones.

### Integraciones (fase 5)
- **Impresión: ticket normal, reporte de cierre y reimpresión HECHOS (2026-10-22)**, `print/` + `domain/PrintLayouts.kt`. PDF 58 mm dibujado con `PdfDocument` y enviado a `PrintManager` (el Sunmi muestra "SunmiPrinter" con vista previa fiel). El éxito se decide por el estado del `PrintJob` (en cola/en curso/completado), **no** por haber escrito el PDF: el servicio escribe para la vista previa antes de confirmar. Cancelar lanza `PrintCancelledException` → la caja no se vacía. Verificado en el equipo: ticket, reimpresión (comprobante original, una sola vez), cancelación y cierre de caja. El reporte guarda copia en `files/pdfs/reporte_DIA_d_m_yy(n).pdf` solo si se imprimió.
  - **Falta:** enlazar el nombre del PDF al cierre guardado en Room (`saveManualClosing(pdfFilename)` recibe `null`), y `informe_dia_*.txt` de Flutter (no portado). Opcional: servicio Sunmi AIDL para imprimir sin diálogo.
  - Diferencia con Flutter: en la sección Anulaciones los valores salen con signo `-` (Flutter lo intentaba pero un error de comparación de título lo omitía). El margen final del PDF usa `pdfEndMargin` (Flutter fijaba 69).
- **NFC** (`NfcAdapter.enableReaderMode`), login y clave por tarjeta; la lógica ya está en `NfcAuth`.
- **SumUp**: intent `sumupmerchant://pay/1.0` (`affiliate-key`, `app-id=posbus`, `amount`, `currency=CLP`, `title`, `callback=posbus://sumup-result`), recibir el resultado en `onNewIntent`/`onCreate` con intent-filter. No usar `<queries>` (rompe el parseo en Sunmi) ni `canLaunchUrl`: lanzar y capturar `ActivityNotFoundException`. Guardar pago con `CardPayments` (ya implementado).
- **Backup**: permisos de almacenamiento en tiempo de ejecución en API 23 y `FileProvider` para compartir.

### Migración y release (fase 6)
- Importar preferencias/DB/`.suray` de la app Flutter (misma clave SumUp incluida).
- Release: R8, `abiFilters armeabi-v7a`, keystore propio (Flutter firma con la clave debug), `versionName` con formato de fecha como hoy.
- Medir rendimiento y RAM en el V1s con Compose.

## Riesgos y cosas a recordar
- La impresión normal y el cierre ya funcionan; los tickets de oferta y cargo también.
- Ventas de días mezclados en un cierre manual se guardan bajo la fecha del reporte (la de la primera venta si todas son del mismo día, si no la fecha actual); no se ha comparado con Flutter.
- Cuando el equipo se conecte de nuevo: comprobar que la app Flutter y la nueva conviven (distinto applicationId).
- Orden sugerido para continuar: ~~Ajustes~~ → ~~impresión (ticket + reporte)~~ → ~~Oferta/Cargo~~ → SumUp (omitido por ahora: falta la clave de afiliado) → NFC → ~~Recuperación~~/~~Estadísticas~~ → ~~Backup~~ → Transferencia/Seguridad/Colores → migración de datos.
