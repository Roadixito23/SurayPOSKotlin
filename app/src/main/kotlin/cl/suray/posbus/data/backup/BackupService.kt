package cl.suray.posbus.data.backup

import cl.suray.posbus.domain.Clock
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.Calendar
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Preferencias de la app tal como las ve el respaldo. */
interface BackupPrefs {
    fun snapshot(): Map<String, Any?>
    fun apply(entries: List<PrefEntry>)
}

/** Base de datos incluida en el respaldo (entrada extra; el Flutter original no la respalda). */
interface BackupDatabase {
    /** Deja el archivo `.db` completo (sin datos pendientes en el WAL) y lo devuelve. */
    fun prepareForCopy(): File
    /** Cierra la base y sustituye el archivo; la app debe reiniciarse después. */
    fun replace(bytes: ByteArray)
}

class BackupException(message: String) : Exception(message)

data class RestoreSummary(val preferences: Int, val pdfs: Int, val cargoReceipts: Int, val databaseRestored: Boolean)

/**
 * `.suray` = ZIP con `preferences.json`, `cargo_receipts/`, `pdfs/` (idéntico al de Flutter) y,
 * como extra, `database/suraypos.db`. Al restaurar se acepta cualquiera de las dos variantes.
 */
class BackupService(
    private val prefs: BackupPrefs,
    private val filesDir: File,
    private val backupDir: File,
    private val database: BackupDatabase?,
    private val clock: Clock,
    /** Claves que no salen del equipo (p. ej. la clave de afiliado de SumUp). */
    private val excludedKeys: Set<String> = emptySet(),
) {
    private val pdfDir get() = File(filesDir, "pdfs")
    private val cargoDir get() = File(filesDir, "cargo_receipts")

    fun create(customName: String = "backup"): File {
        backupDir.mkdirs()
        val name = "${sanitize(customName).ifEmpty { "backup" }}_${timestamp()}$EXTENSION"
        val target = File(backupDir, name)
        val tmp = File(backupDir, "$name.tmp")
        try {
            ZipOutputStream(tmp.outputStream().buffered()).use { zip ->
                val snapshot = prefs.snapshot().filterKeys { it !in excludedKeys }
                zip.put("preferences.json", PrefsBackup.encode(snapshot, isoNow()).toByteArray())
                cargoDir.listFiles { f -> f.isFile }.orEmpty().forEach { zip.put("cargo_receipts/${it.name}", it.readBytes()) }
                pdfDir.listFiles { f -> f.isFile && f.name.endsWith(".pdf", ignoreCase = true) }.orEmpty()
                    .forEach { zip.put("pdfs/${it.name}", it.readBytes()) }
                database?.let { db -> db.prepareForCopy().takeIf { it.exists() }?.let { zip.put("database/${it.name}", it.readBytes()) } }
            }
            if (!tmp.renameTo(target)) throw BackupException("No se pudo guardar la copia de seguridad")
        } finally {
            tmp.delete()
        }
        return target
    }

    /** Respaldos propios, el más reciente primero. */
    fun list(): List<File> =
        backupDir.listFiles { f -> f.isFile && f.name.endsWith(EXTENSION) }.orEmpty().sortedByDescending { it.lastModified() }

    /** Copia un `.suray` externo al directorio de respaldos y lo devuelve. */
    fun import(source: InputStream, originalName: String): File {
        val base = sanitize(originalName.removeSuffix(EXTENSION)).ifEmpty { "importado" }
        backupDir.mkdirs()
        var target = File(backupDir, base + EXTENSION)
        var n = 1
        while (target.exists()) target = File(backupDir, "${base}_${n++}$EXTENSION")
        val bytes = source.use { readCapped(it) }
        if (readEntries(bytes.inputStream()).isEmpty()) throw BackupException("El archivo no es una copia de seguridad válida")
        target.writeBytes(bytes)
        return target
    }

    fun restore(file: File): RestoreSummary {
        val entries = try { readEntries(file.inputStream()) } catch (e: BackupException) { throw e } catch (e: Exception) {
            throw BackupException("No se pudo leer la copia de seguridad")
        }
        val prefsEntry = entries["preferences.json"]
        val db = entries.entries.firstOrNull { it.key.startsWith("database/") && it.key.endsWith(".db") }?.value
        val pdfs = entries.filterKeys { it.startsWith("pdfs/") }
        val cargo = entries.filterKeys { it.startsWith("cargo_receipts/") }
        if (prefsEntry == null && db == null && pdfs.isEmpty() && cargo.isEmpty()) throw BackupException("La copia de seguridad está vacía")

        // Se valida todo antes de tocar el equipo.
        if (db != null && !isSqlite(db)) throw BackupException("La base de datos de la copia está dañada")
        val decoded = prefsEntry?.let {
            try { PrefsBackup.decode(String(it)) } catch (e: Exception) { throw BackupException("Las preferencias de la copia están dañadas") }
        }.orEmpty()

        if (decoded.isNotEmpty()) prefs.apply(decoded)
        if (cargo.isNotEmpty()) {
            cargoDir.deleteRecursively()
            cargoDir.mkdirs()
            cargo.forEach { (path, data) -> safeName(path.removePrefix("cargo_receipts/"))?.let { File(cargoDir, it).writeBytes(data) } }
        }
        var restoredPdfs = 0
        if (pdfs.isNotEmpty()) {
            pdfDir.mkdirs()
            pdfs.forEach { (path, data) -> safeName(path.removePrefix("pdfs/"))?.takeIf { it.endsWith(".pdf", true) }?.let { File(pdfDir, it).writeBytes(data); restoredPdfs++ } }
        }
        val target = database
        val dbRestored = if (db != null && target != null) { target.replace(db); true } else false
        return RestoreSummary(decoded.size, restoredPdfs, cargo.size, dbRestored)
    }

    // --- utilidades ---------------------------------------------------------------------------

    /** Lee el ZIP a memoria con un tope de tamaño; ignora rutas con `..`, absolutas o con subcarpetas inesperadas. */
    private fun readEntries(input: InputStream): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(input.buffered()).use { zip ->
            var e: ZipEntry? = zip.nextEntry
            while (e != null) {
                if (!e.isDirectory && isSafePath(e.name)) {
                    val data = readCapped(zip, MAX_TOTAL_BYTES - total)
                    total += data.size
                    out[e.name] = data
                }
                e = zip.nextEntry
            }
        }
        return out
    }

    private fun readCapped(input: InputStream, limit: Long = MAX_TOTAL_BYTES): ByteArray {
        val buf = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var n = input.read(chunk)
        while (n >= 0) {
            buf.write(chunk, 0, n)
            if (buf.size() > limit) throw BackupException("La copia de seguridad es demasiado grande")
            n = input.read(chunk)
        }
        return buf.toByteArray()
    }

    private fun isSafePath(name: String) = !name.startsWith("/") && name.split('/').none { it == ".." || it.isEmpty() } &&
        (name == "preferences.json" || name.startsWith("pdfs/") || name.startsWith("cargo_receipts/") || name.startsWith("database/"))

    /** Solo el nombre de archivo, sin carpetas (los PDF de Flutter van planos). */
    private fun safeName(relative: String): String? =
        relative.takeIf { it.isNotEmpty() && '/' !in it && '\\' !in it && it != ".." && it != "." }

    private fun ZipOutputStream.put(name: String, data: ByteArray) {
        putNextEntry(ZipEntry(name)); write(data); closeEntry()
    }

    private fun isSqlite(bytes: ByteArray) =
        bytes.size >= SQLITE_MAGIC.size && SQLITE_MAGIC.indices.all { bytes[it] == SQLITE_MAGIC[it] }

    private fun sanitize(name: String) = name.trim().filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(40)

    private fun timestamp(): String {
        val c = Calendar.getInstance().apply { timeInMillis = clock.nowMillis() }
        return "%04d%02d%02d_%02d%02d%02d".format(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND),
        )
    }

    private fun isoNow(): String {
        val c = Calendar.getInstance().apply { timeInMillis = clock.nowMillis() }
        return "%04d-%02d-%02dT%02d:%02d:%02d".format(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND),
        )
    }

    companion object {
        const val EXTENSION = ".suray"
        const val DIR_NAME = "suray_backups"
        private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)
        private const val MAX_TOTAL_BYTES = 200L * 1024 * 1024
    }
}
