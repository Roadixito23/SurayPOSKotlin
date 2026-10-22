# Plan: SurayPOSBus (Flutter) → Kotlin nativo, sin Android Studio, en Sunmi V1s-G (Android 6.0)

## Context
El POS de pasajes de bus (~18k líneas Dart) corre en un Sunmi V1s-G (Android 6.0, API 23, armeabi-v7a) conectado por USB (`adb devices` lo ve: `VS08197Q61659`). Se reescribe en Kotlin nativo, todo desde VS Code + línea de comandos. El proyecto Flutter no se toca.

Este plan consolida y actualiza `~/.claude/plans/necesito-que-hagas-un-mossy-plum.md` (plan detallado de la sesión anterior, que no encontré en mi primera búsqueda).

## Estado actual verificado
- JDK 17, `ANDROID_HOME=~/Android/Sdk` con cmdline-tools, platform-tools (adb), `platforms/android-35` y `android-36`, build-tools 34/35/36. Falta solo aceptar/confirmar licencias si Gradle lo pide.
- No hay emulador ni system-images: se prueba solo en el equipo físico.
- Ya existe `~/projects/Suray/SurayPOSKotlin` (sin commits): Gradle wrapper, `libs.versions.toml`, `local.properties`, manifest, `MainActivity.kt`, y la capa de datos inicial (`data/db`: Entities, Daos, AppDatabase; `data/prefs`: AppPrefs, Models; test instrumentado `DataLayerTest`). Continuar desde ahí, no empezar de cero.

## Decisiones
- UI Jetpack Compose (Material 3), minSdk 23, targetSdk 34, compileSdk 35 (AGP 8.7.3, Kotlin 2.1.0, Gradle 8.11.1). Una Activity + Navigation Compose, MVVM con StateFlow, DI manual.
- Datos: Room (KSP) con el esquema v1 de `lib/database/`; SharedPreferences con las mismas claves que Flutter para poder importar backups.
- Impresión: `PdfDocument` + `PrintManager` a 58 mm (igual que `Printing.layoutPdf`). Opcional después: servicio Sunmi para imprimir sin diálogo.
- applicationId `cl.suray.posbus` (convive con la app Flutter `com.example.untitled3`). Sin `<queries>` en el manifest (rompe el parseo en Sunmi). Mantener `~/.gradle/gradle.properties` (límite de RAM).

## Fases
0. **Toolchain y hello-world en el equipo:** `./gradlew assembleDebug` → `adb install -r app/build/outputs/apk/debug/app-debug.apk` → abrir con `adb shell am start`. Valida la cadena completa en API 23 antes de portar más.
1. **Datos:** terminar Room (`daily_closings`, `transactions`, `weekly_closings`, `monthly_closings`) y `AppPrefs` con las claves de `settings.dart`. Correr `DataLayerTest` en el equipo (`./gradlew connectedDebugAndroidTest`).
2. **Lógica de negocio con tests JVM:** comprobante/ticketId (wrap tras 999999), `ReporteCaja` (anulación = valor negado), `AutoClosingService` (case-insensitive) y `MergeService` (case-sensitive; mantener la diferencia), `HolidayService` (API feriados, caché), estadísticas, transacciones pendientes.
3. **Seguridad:** unificar claves por defecto inconsistentes (`123456`/`232323`/`654321`) y pasar a hash; mover la affiliate key de SumUp fuera del código a `local.properties`/BuildConfig. Decidir con el usuario.
4. **Pantallas Compose, en orden:** Login (clave + NFC) → Home (ventas, domingo/feriado, reloj, anulación, reimpresión) → Reporte de caja → Settings → Cargo → Recovery → Estadísticas → Backup → Transferencia (QR) → Seguridad → Colores. Tema de `lib/theme/app_theme.dart`, fuente Hemiheads.
5. **Integraciones:** impresión (ticket, MO, cargo, reportes), NFC (`enableReaderMode`), SumUp (intent `sumupmerchant://pay/1.0` + callback `posbus://sumup-result`), backup `.suray` (ZIP) con permisos runtime de API 23 y `FileProvider`.
6. **Migración y release:** importar prefs/DB/`.suray` de la app Flutter; R8, `abiFilters armeabi-v7a`, keystore propio.

## Archivos críticos (Flutter, a leer al portar)
`lib/screens/home.dart`, `settings.dart`, `reporte_caja_screen.dart`, `lib/models/ReporteCaja.dart`, `lib/services/closing/*`, `lib/services/pdf/*`, `lib/services/sumup_service.dart`, `lib/services/backup/backup_service.dart`, `lib/database/*`, `lib/theme/*`.

## Verificación
- `./gradlew testDebugUnitTest`, `./gradlew assembleDebug`, `adb install -r`, `adb logcat` filtrado por el paquete.
- Checklist manual en el V1s-G: venta, anulación, cierre/auto-cierre, feriado, impresión 58 mm (comparar con Flutter), NFC, SumUp, backup/restore, bloqueo de claves, rendimiento (RAM/CPU).

## Riesgos
Impresión (depende del hardware), RAM del V1s con Compose, credencial SumUp en el código, defaults de clave inconsistentes, sin emulador.

## Notas de la exploración final del código Flutter
- El manifest principal no declara `INTERNET` (solo debug/profile): en la versión Kotlin declararlo, o `HolidayService` fallará en release.
- La affiliate key de SumUp está hardcodeada en `sumup_service.dart`; no copiarla a archivos versionados.
- No hay tests, ni código nativo propio, ni QR dinámico (los QR son imágenes estáticas). `excel` y `MaintenanceService` son código muerto: no portar.
- `ReporteRecoveryPDF.dart` es un placeholder.
- Sin `<queries>` a propósito (bug de parseo en Sunmi); en Kotlin, no usar `canLaunchUrl`: lanzar el intent y capturar `ActivityNotFoundException`.
- Pendiente de confirmar: si el backup `.suray` incluye la DB SQLite (`backup_service.dart`).
