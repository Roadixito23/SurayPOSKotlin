package cl.suray.posbus.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cl.suray.posbus.AppContainer
import cl.suray.posbus.data.prefs.LastTransaction
import cl.suray.posbus.data.prefs.Tarifa
import cl.suray.posbus.domain.CANCELLATION_PREFIX
import cl.suray.posbus.domain.DAYS_ES_UPPER
import cl.suray.posbus.domain.DateParts
import cl.suray.posbus.domain.LocalDay
import cl.suray.posbus.domain.SumUpConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MessageKind { Info, Success, Warning, Error }

data class UiMessage(val text: String, val kind: MessageKind = MessageKind.Info)

data class HomeUiState(
    val dayName: String = "",
    val dateText: String = "",
    val nextComprobante: Int = 1,
    val sundayRate: Boolean = false,
    val autoRate: Boolean = false,
    val holidayReason: String? = null,
    val pendingDays: Int = 0,
    val salesToday: Int = 0,
    val cancellationsToday: Int = 0,
    val canCancel: Boolean = false,
    val canReprint: Boolean = false,
    val fares: List<Tarifa> = emptyList(),
    val busy: Boolean = false,
    val cardAvailable: Boolean = false,
) {
    val hasPending: Boolean get() = pendingDays > 0
}

class HomeViewModel(private val c: AppContainer) : ViewModel() {

    private val sundayRate = MutableStateFlow(false)
    private val autoRate = MutableStateFlow(false)
    private val holidayReason = MutableStateFlow<String?>(null)
    private val busy = MutableStateFlow(false)
    private val hasReprinted = MutableStateFlow(false)
    private val hasCancelled = MutableStateFlow(false)
    private val lastTransaction = MutableStateFlow(c.prefs.lastTransaction)
    private val today = MutableStateFlow(LocalDay.of(c.clock.nowMillis()))
    private val pendingDays = MutableStateFlow(0)

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private val _pendingAlert = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    /** Días pendientes cuando hay que mostrar el diálogo de cierre obligatorio. */
    val pendingAlert: SharedFlow<Int> = _pendingAlert.asSharedFlow()

    val display = DisplayPrefs(
        showIcons = c.prefs.showIcons,
        textSizeMultiplier = c.prefs.textSizeMultiplier,
    )
    val colors: ButtonPalette = ButtonPalette.load(c.prefs)

    private val base = combine(c.register.changes, c.comprobante.numberState, today, pendingDays) { open, number, day, pending ->
        val dia = "%02d".format(day.day)
        val mes = "%02d".format(day.month)
        val todays = open.filter { it.dia == dia && it.mes == mes }
        HomeUiState(
            dayName = DAYS_ES_UPPER[day.dayOfWeek - 1],
            dateText = "%02d/%02d/%02d".format(day.day, day.month, day.year % 100),
            nextComprobante = number + 1,
            pendingDays = pending,
            salesToday = todays.count { !it.nombre.startsWith(CANCELLATION_PREFIX) },
            cancellationsToday = todays.count { it.nombre.startsWith(CANCELLATION_PREFIX) },
            canCancel = open.isNotEmpty(),
        )
    }

    val state: StateFlow<HomeUiState> = combine(
        base, sundayRate, autoRate, holidayReason, combine(busy, hasReprinted, hasCancelled, lastTransaction) { a, b, d, e -> listOf(a, b, d, e) },
    ) { s, sunday, auto, reason, flags ->
        val isBusy = flags[0] as Boolean
        val reprinted = flags[1] as Boolean
        val cancelled = flags[2] as Boolean
        val last = flags[3] as LastTransaction?
        val isCargo = last?.nombre?.lowercase()?.contains("cargo") == true
        s.copy(
            sundayRate = sunday,
            autoRate = auto,
            holidayReason = reason,
            canCancel = s.canCancel && !cancelled,
            canReprint = last != null && (isCargo || !reprinted),
            fares = if (sunday) c.prefs.sundayFares else c.prefs.weekdayFares,
            busy = isBusy,
            cardAvailable = SumUpConfig.isReady(c.prefs.sumUpEnabled, c.prefs.sumUpAffiliateKey),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    init {
        viewModelScope.launch {
            while (true) {
                today.value = LocalDay.of(c.clock.nowMillis())
                pendingDays.value = if (c.register.hasPendingOld()) c.register.oldestPendingDays().coerceAtLeast(1) else 0
                delay(1_000)
            }
        }
        viewModelScope.launch { detectRate() }
        viewModelScope.launch {
            c.pending.startPeriodicCheck(viewModelScope) {
                c.pending.markAlertShown()
                _pendingAlert.tryEmit(c.pending.pendingDays())
            }
            if (c.pending.hasPreviousDayTransactions()) {
                c.pending.markAlertShown()
                _pendingAlert.tryEmit(c.pending.pendingDays())
            }
        }
    }

    /** Domingo o feriado activa la tarifa especial; el usuario puede cambiarla a mano después. */
    private suspend fun detectRate() {
        val result = withContext(Dispatchers.IO) { runCatching { c.holidays.checkToday() }.getOrNull() } ?: return
        sundayRate.value = result.isSpecial
        autoRate.value = result.isSpecial
        holidayReason.value = result.reason
    }

    fun setSundayRate(value: Boolean) {
        sundayRate.value = value
        autoRate.value = false
        holidayReason.value = null
    }

    fun alertDismissed() = c.pending.onAlertDismissed(viewModelScope)

    /** Venta en efectivo: cierre automático → comprobante → caja → última venta → impresión. */
    fun sell(tipo: String, valor: Double) {
        if (busy.value) return
        viewModelScope.launch {
            if (c.closing.checkAndAutoClose()) {
                _messages.tryEmit(UiMessage("Se cerró la caja del día anterior automáticamente"))
            }
            if (c.pending.hasPreviousDayTransactions()) {
                _pendingAlert.tryEmit(c.pending.pendingDays())
                return@launch
            }
            busy.value = true
            hasReprinted.value = false
            hasCancelled.value = false
            _messages.tryEmit(UiMessage("Generando ticket de $tipo…"))

            // El comprobante se incrementa antes de escribir en la caja, como en Flutter.
            c.comprobante.increment()
            val comprobante = c.comprobante.formatted
            val saved = runCatching { c.register.receiveData(tipo, valor, comprobante) }
            if (saved.isFailure) {
                _messages.tryEmit(UiMessage("Error al guardar venta. Contacte soporte.", MessageKind.Error))
                busy.value = false
                return@launch
            }
            c.closing.updateLastTransactionDate()
            val last = LastTransaction(tipo, valor, comprobante, sundayRate.value)
            c.prefs.lastTransaction = last
            lastTransaction.value = last

            // La impresión es independiente: un fallo no revierte la venta.
            runCatching { c.printer.printTicket(tipo, valor, comprobante, sundayRate.value) }
                .onSuccess { _messages.tryEmit(UiMessage("Ticket generado correctamente", MessageKind.Success)) }
                .onFailure { _messages.tryEmit(UiMessage("Venta guardada. Error al imprimir ticket.", MessageKind.Warning)) }
            busy.value = false
        }
    }

    /** Hasta portar SumUp y las pantallas de oferta/cargo, estos botones avisan en vez de fallar en silencio. */
    fun notifyCardPending() { _messages.tryEmit(UiMessage("Pago con tarjeta (SumUp): disponible en la fase de integraciones", MessageKind.Warning)) }

    fun notifyPending(what: String) { _messages.tryEmit(UiMessage("$what: pantalla pendiente de portar", MessageKind.Warning)) }

    fun verifyPassword(input: String) = c.passwords.verifyLogin(input)

    /** Anula la última venta (la caja agrega la línea negativa y el contador retrocede 1). */
    fun cancelLast() {
        viewModelScope.launch {
            if (c.register.cancelLast()) {
                c.comprobante.rollbackLast()
                hasCancelled.value = true
                _messages.tryEmit(UiMessage("Última venta anulada."))
            }
        }
    }

    override fun onCleared() {
        c.pending.stop()
    }

    class Factory(private val c: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(c) as T
    }
}

data class DisplayPrefs(val showIcons: Boolean, val textSizeMultiplier: Float)
