package cl.suray.posbus

import android.app.Activity
import android.content.Context
import cl.suray.posbus.print.AndroidPrinter
import cl.suray.posbus.data.db.AppDatabase
import cl.suray.posbus.data.prefs.AppPrefs
import cl.suray.posbus.domain.CardPayments
import cl.suray.posbus.domain.CashRegister
import cl.suray.posbus.domain.CloseRegister
import cl.suray.posbus.domain.ReportPrinter
import cl.suray.posbus.domain.ClosingService
import cl.suray.posbus.domain.ComprobanteCounter
import cl.suray.posbus.domain.HolidayService
import cl.suray.posbus.domain.LockoutPolicy
import cl.suray.posbus.domain.PasswordManager
import cl.suray.posbus.domain.PendingTransactionService
import cl.suray.posbus.domain.SystemClock
import cl.suray.posbus.domain.TicketPrinter
import kotlinx.coroutines.flow.MutableStateFlow

/** Inyección de dependencias manual (sin Hilt, para mantener ligero el build y el arranque en el V1s). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val prefs = AppPrefs(appContext)
    val db: AppDatabase by lazy { AppDatabase.get(appContext) }
    val clock = SystemClock

    val register = CashRegister(prefs, clock)
    val comprobante = ComprobanteCounter(prefs)
    val passwords = PasswordManager(prefs).also { it.seedDefaults() }
    val lockout = LockoutPolicy(prefs, clock)
    val closing: ClosingService by lazy { ClosingService(db, prefs, register, clock) }
    val holidays = HolidayService(prefs)
    val pending = PendingTransactionService(register)

    /** Se incrementa al guardar Ajustes; Home lo observa para releer tarifas y preferencias de pantalla. */
    val settingsRevision = MutableStateFlow(0)

    val cardPayments = CardPayments(prefs, clock)

    /** La Activity visible; el servicio de impresión de Android la necesita para abrir su diálogo. */
    @Volatile var currentActivity: Activity? = null

    private val androidPrinter = AndroidPrinter(appContext, { currentActivity }, prefs, clock)
    var printer: TicketPrinter = androidPrinter
    var reportPrinter: ReportPrinter = androidPrinter

    val closeRegister: CloseRegister by lazy { CloseRegister(register, closing, { reportPrinter }, clock) }
}
