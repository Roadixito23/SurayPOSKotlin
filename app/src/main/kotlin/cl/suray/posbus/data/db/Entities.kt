package cl.suray.posbus.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Categoría de transacción; se guarda como texto (`pasaje`, `correspondencia`, `anulacion`). */
enum class TransactionCategory(val dbValue: String) {
    PASAJE("pasaje"),
    CORRESPONDENCIA("correspondencia"),
    ANULACION("anulacion");

    companion object {
        fun fromDb(value: String): TransactionCategory =
            entries.firstOrNull { it.dbValue == value } ?: PASAJE
    }
}

@Entity(
    tableName = "daily_closings",
    indices = [
        Index(value = ["date"], unique = true, name = "idx_daily_closings_date"),
        Index(value = ["year", "month"], name = "idx_daily_closings_year_month"),
    ],
)
data class DailyClosing(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // yyyy-MM-dd
    val year: Int,
    val month: Int,
    val day: Int,
    @ColumnInfo(name = "day_of_week") val dayOfWeek: String,
    @ColumnInfo(name = "total_pasajes") val totalPasajes: Double = 0.0,
    @ColumnInfo(name = "total_correspondencias") val totalCorrespondencias: Double = 0.0,
    @ColumnInfo(name = "total_anulaciones") val totalAnulaciones: Double = 0.0,
    @ColumnInfo(name = "grand_total") val grandTotal: Double = 0.0,
    @ColumnInfo(name = "transaction_count") val transactionCount: Int = 0,
    @ColumnInfo(name = "ticket_id") val ticketId: String? = null,
    val status: String = "closed",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "closed_at") val closedAt: Long,
    @ColumnInfo(name = "pdf_filename") val pdfFilename: String? = null,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = DailyClosing::class,
            parentColumns = ["id"],
            childColumns = ["daily_closing_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["daily_closing_id"], name = "idx_transactions_daily_closing"),
        Index(value = ["category"], name = "idx_transactions_category"),
        Index(value = ["timestamp"], name = "idx_transactions_timestamp"),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "daily_closing_id") val dailyClosingId: Long,
    /** ID original de la venta en la caja abierta. */
    @ColumnInfo(name = "transaction_id") val transactionId: Int,
    val nombre: String,
    val valor: Double,
    val comprobante: String,
    val dia: String,
    val mes: String,
    val ano: String,
    val hora: String,
    val timestamp: Long,
    val category: String,
    @ColumnInfo(name = "is_cancellation") val isCancellation: Boolean = false,
)

@Entity(
    tableName = "weekly_closings",
    indices = [Index(value = ["year", "week_number"], unique = true, name = "idx_weekly_closings_year_week")],
)
data class WeeklyClosing(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val year: Int,
    @ColumnInfo(name = "week_number") val weekNumber: Int,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String,
    @ColumnInfo(name = "total_pasajes") val totalPasajes: Double = 0.0,
    @ColumnInfo(name = "total_correspondencias") val totalCorrespondencias: Double = 0.0,
    @ColumnInfo(name = "total_anulaciones") val totalAnulaciones: Double = 0.0,
    @ColumnInfo(name = "grand_total") val grandTotal: Double = 0.0,
    @ColumnInfo(name = "transaction_count") val transactionCount: Int = 0,
    @ColumnInfo(name = "daily_closing_count") val dailyClosingCount: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "pdf_filename") val pdfFilename: String? = null,
)

@Entity(
    tableName = "monthly_closings",
    indices = [Index(value = ["year", "month"], unique = true, name = "idx_monthly_closings_year_month")],
)
data class MonthlyClosing(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val year: Int,
    val month: Int,
    @ColumnInfo(name = "month_name") val monthName: String,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String,
    @ColumnInfo(name = "total_pasajes") val totalPasajes: Double = 0.0,
    @ColumnInfo(name = "total_correspondencias") val totalCorrespondencias: Double = 0.0,
    @ColumnInfo(name = "total_anulaciones") val totalAnulaciones: Double = 0.0,
    @ColumnInfo(name = "grand_total") val grandTotal: Double = 0.0,
    @ColumnInfo(name = "transaction_count") val transactionCount: Int = 0,
    @ColumnInfo(name = "daily_closing_count") val dailyClosingCount: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "pdf_filename") val pdfFilename: String? = null,
)
