package cl.suray.posbus.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DailyClosing::class, TransactionEntity::class, WeeklyClosing::class, MonthlyClosing::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyClosingDao(): DailyClosingDao
    abstract fun transactionDao(): TransactionDao
    abstract fun weeklyClosingDao(): WeeklyClosingDao
    abstract fun monthlyClosingDao(): MonthlyClosingDao

    companion object {
        const val NAME = "suraypos.db"

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                    .build()
                    .also { instance = it }
            }
    }
}
