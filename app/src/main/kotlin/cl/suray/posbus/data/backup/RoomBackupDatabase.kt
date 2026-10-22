package cl.suray.posbus.data.backup

import android.content.Context
import cl.suray.posbus.data.db.AppDatabase
import java.io.File

class RoomBackupDatabase(private val context: Context, private val db: () -> AppDatabase) : BackupDatabase {
    private val file: File get() = context.getDatabasePath(AppDatabase.NAME)

    override fun prepareForCopy(): File {
        // Vuelca el WAL al archivo principal para que la copia esté completa.
        db().openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        return file
    }

    override fun replace(bytes: ByteArray) {
        db().close()
        File(file.path + "-wal").delete()
        File(file.path + "-shm").delete()
        file.writeBytes(bytes)
    }
}
