package cl.suray.posbus.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

data class CategoryTotal(val category: String, val total: Double)

@Dao
interface DailyClosingDao {
    @Insert
    suspend fun insert(closing: DailyClosing): Long

    @Update
    suspend fun update(closing: DailyClosing): Int

    @Query("SELECT * FROM daily_closings WHERE date = :date")
    suspend fun getByDate(date: String): DailyClosing?

    @Query("SELECT * FROM daily_closings WHERE id = :id")
    suspend fun getById(id: Long): DailyClosing?

    @Query("SELECT * FROM daily_closings WHERE date >= :start AND date <= :end ORDER BY date ASC")
    suspend fun getInRange(start: String, end: String): List<DailyClosing>

    @Query("SELECT * FROM daily_closings ORDER BY date DESC")
    suspend fun getAll(): List<DailyClosing>

    @Query("SELECT * FROM daily_closings WHERE year = :year AND month = :month ORDER BY day ASC")
    suspend fun getByYearMonth(year: Int, month: Int): List<DailyClosing>

    /** Las transacciones se eliminan en cascada. */
    @Query("DELETE FROM daily_closings WHERE id = :id")
    suspend fun delete(id: Long): Int
}

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Query("SELECT * FROM transactions WHERE daily_closing_id = :closingId ORDER BY transaction_id ASC")
    suspend fun getByClosingId(closingId: Long): List<TransactionEntity>

    @Query(
        "SELECT * FROM transactions WHERE daily_closing_id = :closingId AND category = :category " +
            "ORDER BY transaction_id ASC",
    )
    suspend fun getByCategory(closingId: Long, category: String): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE daily_closing_id = :closingId")
    suspend fun count(closingId: Long): Int

    @Query("SELECT * FROM transactions ORDER BY timestamp ASC")
    suspend fun getAllByTimestamp(): List<TransactionEntity>

    @Query("SELECT category, SUM(valor) AS total FROM transactions WHERE daily_closing_id = :closingId GROUP BY category")
    suspend fun totalsByCategory(closingId: Long): List<CategoryTotal>
}

@Dao
interface WeeklyClosingDao {
    @Insert
    suspend fun insert(closing: WeeklyClosing): Long

    @Update
    suspend fun update(closing: WeeklyClosing): Int

    @Query("SELECT * FROM weekly_closings WHERE year = :year AND week_number = :week")
    suspend fun getByYearWeek(year: Int, week: Int): WeeklyClosing?

    @Query("SELECT * FROM weekly_closings WHERE year = :year ORDER BY week_number ASC")
    suspend fun getByYear(year: Int): List<WeeklyClosing>

    @Query("SELECT * FROM weekly_closings ORDER BY year DESC, week_number DESC")
    suspend fun getAll(): List<WeeklyClosing>

    @Query("DELETE FROM weekly_closings WHERE id = :id")
    suspend fun delete(id: Long): Int
}

@Dao
interface MonthlyClosingDao {
    @Insert
    suspend fun insert(closing: MonthlyClosing): Long

    @Update
    suspend fun update(closing: MonthlyClosing): Int

    @Query("SELECT * FROM monthly_closings WHERE year = :year AND month = :month")
    suspend fun getByYearMonth(year: Int, month: Int): MonthlyClosing?

    @Query("SELECT * FROM monthly_closings WHERE year = :year ORDER BY month ASC")
    suspend fun getByYear(year: Int): List<MonthlyClosing>

    @Query("SELECT * FROM monthly_closings ORDER BY year DESC, month DESC")
    suspend fun getAll(): List<MonthlyClosing>

    @Query("DELETE FROM monthly_closings WHERE id = :id")
    suspend fun delete(id: Long): Int
}
