package cl.suray.posbus.data

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import cl.suray.posbus.data.backup.BackupService
import cl.suray.posbus.data.prefs.AppPrefs
import cl.suray.posbus.domain.Clock
import cl.suray.posbus.domain.PasswordManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Importa en `SharedPreferences` reales un `.suray` con el formato y los tipos del respaldo de Flutter
 * (enteros, dobles, JSON dentro de strings) y comprueba que todos los getters de [AppPrefs] leen sin lanzar
 * (un tipo guardado equivocado produce `ClassCastException`).
 */
class FlutterMigrationTest {
    private lateinit var context: Context
    private lateinit var dir: File
    private lateinit var prefs: AppPrefs

    private val flutterPrefs = """{
      "password":"482913","password_type":"string",
      "settings_password":"777888","settings_password_type":"string",
      "comprobanteSettingsPassword":"135790","comprobanteSettingsPassword_type":"string",
      "pasajes":"[{\"nombre\":\"Público General\",\"precio\":3800.0},{\"nombre\":\"Escolar General\",\"precio\":2600}]","pasajes_type":"string",
      "sunday_pasajes":"[{\"nombre\":\"Público General\",\"precio\":4400.0}]","sunday_pasajes_type":"string",
      "reporte_caja_transactions":"[{\"id\":4,\"nombre\":\"Público General\",\"valor\":3800.0,\"comprobante\":\"01-000007\",\"dia\":\"21\",\"mes\":\"10\",\"ano\":\"2026\",\"hora\":\"09:15\",\"timestamp\":1792574100000}]","reporte_caja_transactions_type":"string",
      "reporte_caja_next_id":5,"reporte_caja_next_id_type":"int",
      "comprobanteNumber":7,"comprobanteNumber_type":"int",
      "ticketId":2,"ticketId_type":"int",
      "last_transaction_date":"2026-10-21","last_transaction_date_type":"string",
      "lastTransaction":"{\"nombre\":\"Público General\",\"valor\":3800.0,\"comprobante\":\"01-000007\",\"switchValue\":false}","lastTransaction_type":"string",
      "showIcons":false,"showIcons_type":"bool",
      "textSizeMultiplier":0.9,"textSizeMultiplier_type":"double",
      "buttonOpacity":0.8,"buttonOpacity_type":"double",
      "iconSpacing":1.0,"iconSpacing_type":"double",
      "pdfEndMargin":40.0,"pdfEndMargin_type":"double",
      "sumUpEnabled":true,"sumUpEnabled_type":"bool",
      "sumup_affiliate_key":"clave-de-prueba","sumup_affiliate_key_type":"string",
      "linked_nfc_cards":"[\"04A1B2C3\"]","linked_nfc_cards_type":"string",
      "holidays_cache_2026":"[]","holidays_cache_2026_type":"string",
      "holidays_fungible_2026":true,"holidays_fungible_2026_type":"bool",
      "failedAttempts":2,"failedAttempts_type":"int",
      "lastLockTime":1792574100000,"lastLockTime_type":"int",
      "button_colors_weekday_0":"{\"bgRed\":10.0,\"bgGreen\":20.0,\"bgBlue\":30.0,\"bgOpacity\":0.5,\"borderRed\":0.0,\"borderGreen\":0.0,\"borderBlue\":0.0}","button_colors_weekday_0_type":"string",
      "pagos_tarjeta_2026-10-21":"[]","pagos_tarjeta_2026-10-21_type":"string",
      "__backup_date":"2026-10-21T10:00:00.000","__backup_version":"1.0"
    }"""

    @Before fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        dir = File(context.cacheDir, "migration_test").also { it.deleteRecursively(); it.mkdirs() }
        val sp = context.getSharedPreferences("test_migration", Context.MODE_PRIVATE)
        sp.edit().clear().commit()
        prefs = AppPrefs(sp)
    }

    @After fun tearDown() {
        dir.deleteRecursively()
        context.getSharedPreferences("test_migration", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun flutterBackupImportsAndEveryGetterReads() {
        val zip = File(dir, "flutter.suray")
        ZipOutputStream(zip.outputStream()).use { z ->
            fun put(n: String, d: ByteArray) { z.putNextEntry(ZipEntry(n)); z.write(d); z.closeEntry() }
            put("preferences.json", flutterPrefs.toByteArray())
            put("pdfs/reporte_MIÉRCOLES_21_10_26(1).pdf", byteArrayOf(37, 80, 68, 70))
            put("cargo_receipts/recibo1.pdf", byteArrayOf(1))
        }
        val service = BackupService(prefs, File(dir, "files"), File(dir, "backups"), null, Clock { 0L })
        val summary = service.restore(zip)

        assertTrue(summary.preferences >= 25)
        assertEquals(1, summary.pdfs)
        assertEquals(1, summary.cargoReceipts)
        assertFalse(summary.databaseRestored) // Flutter no respalda SQLite

        // Todos los getters deben leer sin ClassCastException y con el valor importado.
        assertEquals(2, prefs.weekdayFares.size)
        assertEquals(3800.0, prefs.weekdayFares[0].precio, 0.0)
        assertEquals(2600.0, prefs.weekdayFares[1].precio, 0.0)
        assertEquals(4400.0, prefs.sundayFares[0].precio, 0.0)
        assertEquals(1, prefs.openTransactions.size)
        assertEquals("01-000007", prefs.openTransactions[0].comprobante)
        assertEquals(5, prefs.openNextId)
        assertEquals(7, prefs.comprobanteNumber)
        assertEquals(2, prefs.ticketId)
        assertEquals("2026-10-21", prefs.lastTransactionDate)
        assertEquals("01-000007", prefs.lastTransaction!!.comprobante)
        assertFalse(prefs.showIcons)
        assertEquals(0.9f, prefs.textSizeMultiplier, 0f)
        assertEquals(0.8f, prefs.buttonOpacity, 0f)
        assertEquals(1.0f, prefs.iconSpacing, 0f)
        assertEquals(40f, prefs.pdfEndMargin, 0f)
        assertTrue(prefs.sumUpEnabled)
        assertEquals("clave-de-prueba", prefs.sumUpAffiliateKey)
        assertEquals(listOf("04A1B2C3"), prefs.linkedNfcCards)
        assertEquals(true, prefs.holidaysFungible(2026))
        assertNotNull(prefs.holidaysCache(2026))
        assertEquals(2, prefs.failedAttempts)
        assertEquals(1792574100000L, prefs.lastLockTime)
        assertNotNull(prefs.buttonColorsJson("weekday_0"))
        assertEquals("[]", prefs.cardPayments("2026-10-21"))

        // Las claves importadas son las que valida la app.
        val pw = PasswordManager(prefs)
        assertTrue(pw.verifyLogin("482913"))
        assertTrue(pw.verifySettings("777888"))
        assertTrue(pw.verifyComprobante("135790"))
    }
}
