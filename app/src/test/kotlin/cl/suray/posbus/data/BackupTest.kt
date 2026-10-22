package cl.suray.posbus.data

import cl.suray.posbus.data.backup.BackupDatabase
import cl.suray.posbus.data.backup.BackupException
import cl.suray.posbus.data.backup.BackupPrefs
import cl.suray.posbus.data.backup.BackupService
import cl.suray.posbus.data.backup.PrefEntry
import cl.suray.posbus.data.backup.PrefType
import cl.suray.posbus.data.backup.PrefsBackup
import cl.suray.posbus.domain.Clock
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrefsBackupTest {
    @Test fun roundTripKeepsTypes() {
        val json = PrefsBackup.encode(
            mapOf("a" to "x", "b" to true, "c" to 5, "d" to 0.8f, "e" to 1_700_000_000_000L, "f" to setOf("u")),
            "2026-10-22T10:00:00",
        )
        assertTrue(json.contains("\"d\":0.8"))
        val byKey = PrefsBackup.decode(json).associateBy { it.key }
        assertEquals(PrefEntry("a", PrefType.String, "x"), byKey["a"])
        assertEquals(PrefEntry("b", PrefType.Bool, true), byKey["b"])
        assertEquals(PrefEntry("c", PrefType.Int, 5L), byKey["c"])
        assertEquals(PrefEntry("d", PrefType.Double, 0.8), byKey["d"])
        assertEquals(PrefEntry("e", PrefType.Int, 1_700_000_000_000L), byKey["e"])
        assertEquals(PrefEntry("f", PrefType.StringList, listOf("u")), byKey["f"])
        assertFalse(byKey.keys.any { it.startsWith("__") || it.endsWith("_type") })
    }

    @Test fun readsFlutterFormat() {
        val flutter = """{"password":"123456","password_type":"string","showIcons":true,"showIcons_type":"bool",
            "buttonOpacity":1.0,"buttonOpacity_type":"double","ticketId":2,"ticketId_type":"int",
            "__backup_date":"2026-01-01T00:00:00","__backup_version":"1.0"}"""
        val e = PrefsBackup.decode(flutter).associateBy { it.key }
        assertEquals(4, e.size)
        assertEquals(1.0, e["buttonOpacity"]!!.value)
        assertEquals(2L, e["ticketId"]!!.value)
    }

    @Test fun inferredWhenNoMarkerAndGarbageIgnored() {
        val e = PrefsBackup.decode("""{"x":3,"y":"t","z":null}""").associateBy { it.key }
        assertEquals(PrefType.Int, e["x"]!!.type)
        assertEquals(PrefType.String, e["y"]!!.type)
        assertFalse("z" in e)
        assertTrue(PrefsBackup.decode("[1,2]").isEmpty())
    }
}

class BackupServiceTest {
    @get:Rule val tmp = TemporaryFolder()

    private class FakePrefs(var data: MutableMap<String, Any?> = mutableMapOf()) : BackupPrefs {
        var applied: List<PrefEntry> = emptyList()
        override fun snapshot() = data
        override fun apply(entries: List<PrefEntry>) { applied = entries }
    }

    private class FakeDb(val file: File) : BackupDatabase {
        var replaced: ByteArray? = null
        override fun prepareForCopy() = file
        override fun replace(bytes: ByteArray) { replaced = bytes }
    }

    private val clock = Clock { 1_760_000_000_000L }
    private val SQLITE = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)

    @Test fun corruptDatabaseAbortsBeforeTouchingAnything() {
        val src = tmp.newFolder("s")
        val dbFile = File(src, "suraypos.db").apply { writeBytes(byteArrayOf(9, 9, 9)) }
        val backup = service(FakePrefs(mutableMapOf("a" to "b")), src, FakeDb(dbFile)).create()
        val dstPrefs = FakePrefs()
        val dstDb = FakeDb(File(tmp.newFolder("d"), "x.db"))
        try { service(dstPrefs, tmp.newFolder("d2"), dstDb).restore(backup); fail("debía fallar") } catch (e: BackupException) { /* ok */ }
        assertTrue(dstPrefs.applied.isEmpty())
        assertEquals(null, dstDb.replaced)
    }

    private fun service(prefs: BackupPrefs, files: File, db: BackupDatabase? = null, excluded: Set<String> = emptySet()) =
        BackupService(prefs, files, File(files, "suray_backups"), db, clock, excluded)

    @Test fun createThenRestoreRoundTrip() {
        val src = tmp.newFolder("src")
        File(src, "pdfs").mkdirs()
        File(src, "pdfs/reporte_A(1).pdf").writeBytes(byteArrayOf(1, 2, 3))
        File(src, "pdfs/notas.txt").writeText("no")
        val dbFile = File(src, "suraypos.db").apply { writeBytes(SQLITE + byteArrayOf(9, 9)) }
        val prefs = FakePrefs(mutableMapOf("ticketId" to 3, "sumup_affiliate_key" to "secreto", "password" to "123456"))
        val backup = service(prefs, src, FakeDb(dbFile), setOf("sumup_affiliate_key")).create("mi copia/../x")

        assertTrue(backup.name.matches(Regex("micopiax_\\d{8}_\\d{6}\\.suray")))

        val dst = tmp.newFolder("dst")
        val dstPrefs = FakePrefs()
        val dstDb = FakeDb(File(dst, "x.db"))
        val summary = service(dstPrefs, dst, dstDb).restore(backup)

        assertEquals(2, summary.preferences)
        assertEquals(1, summary.pdfs)
        assertTrue(summary.databaseRestored)
        assertFalse("la clave excluida no viaja", dstPrefs.applied.any { it.key == "sumup_affiliate_key" })
        assertEquals(listOf<Byte>(1, 2, 3), File(dst, "pdfs/reporte_A(1).pdf").readBytes().toList())
        assertFalse(File(dst, "pdfs/notas.txt").exists())
        assertEquals((SQLITE + byteArrayOf(9, 9)).toList(), dstDb.replaced!!.toList())
    }

    @Test fun listIsNewestFirst() {
        val dir = tmp.newFolder()
        val s = service(FakePrefs(), dir)
        val old = s.create("a").also { it.setLastModified(1000) }
        val new = s.create("b").also { it.setLastModified(5000) }
        assertEquals(listOf(new, old), s.list())
    }

    @Test fun restoreIgnoresPathTraversalEntries() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                fun put(n: String, d: String) { z.putNextEntry(ZipEntry(n)); z.write(d.toByteArray()); z.closeEntry() }
                put("preferences.json", """{"a":"b","a_type":"string"}""")
                put("pdfs/../../evil.pdf", "x")
                put("/abs/evil.pdf", "x")
                put("pdfs/sub/dir.pdf", "x")
                put("pdfs/ok.pdf", "ok")
            }
        }.toByteArray()
        val dir = tmp.newFolder()
        val file = File(dir, "x.suray").apply { writeBytes(bytes) }
        val summary = service(FakePrefs(), dir).restore(file)
        assertEquals(1, summary.pdfs)
        assertTrue(File(dir, "pdfs/ok.pdf").exists())
        assertFalse(File(dir.parentFile, "evil.pdf").exists())
        assertFalse(File(dir, "pdfs/dir.pdf").exists())
    }

    @Test fun invalidFileIsRejected() {
        val dir = tmp.newFolder()
        val bad = File(dir, "bad.suray").apply { writeText("esto no es un zip") }
        try { service(FakePrefs(), dir).restore(bad); fail("debía fallar") } catch (e: BackupException) { /* ok */ }
        try { service(FakePrefs(), dir).import("basura".byteInputStream(), "x.suray"); fail("debía fallar") } catch (e: BackupException) { /* ok */ }
    }

    @Test fun importAvoidsOverwriting() {
        val dir = tmp.newFolder()
        val s = service(FakePrefs(), dir)
        val made = s.create("base")
        val a = s.import(made.inputStream(), "copia.suray")
        val b = s.import(made.inputStream(), "copia.suray")
        assertEquals("copia.suray", a.name)
        assertEquals("copia_1.suray", b.name)
    }
}
