package cl.suray.posbus.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cl.suray.posbus.AppContainer
import cl.suray.posbus.data.prefs.CargoData
import cl.suray.posbus.data.prefs.LastTransaction
import cl.suray.posbus.data.prefs.OfferEntry
import cl.suray.posbus.domain.CARGO_PREFIX
import cl.suray.posbus.domain.CargoCopy
import cl.suray.posbus.domain.OFFER_NAME
import cl.suray.posbus.domain.Offers
import cl.suray.posbus.data.prefs.Tarifa
import cl.suray.posbus.domain.AutoCloseResult
import cl.suray.posbus.domain.CANCELLATION_PREFIX
import cl.suray.posbus.domain.DAYS_ES_UPPER
import cl.suray.posbus.domain.DateParts
import cl.suray.posbus.domain.LocalDay
import cl.suray.posbus.domain.SumUpConfig
import cl.suray.posbus.domain.cancelLastSale
import cl.suray.posbus.domain.recordSale
import cl.suray.posbus.domain.runCatchingCancellable
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
    val lastIsCargo: Boolean = false,
    val display: DisplayPrefs = DisplayPrefs(showIcons = true, textSizeMultiplier = 0.8f),
    val colors: ButtonPalette = ButtonPalette(emptyMap()),
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
        base, sundayRate, autoRate, holidayReason, combine(busy, hasReprinted, hasCancelled, lastTransaction, c.settingsRevision) { a, b, d, e, _ -> listOf(a, b, d, e) },
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
            lastIsCargo = isCargo,
            cardAvailable = SumUpConfig.isReady(c.prefs.sumUpEnabled, c.prefs.sumUpAffiliateKey),
            display = DisplayPrefs(c.prefs.showIcons, c.prefs.textSizeMultiplier),
            colors = ButtonPalette.load(c.prefs),
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
        val sunday = sundayRate.value
        launchSale(
            announce = "Generando ticket de $tipo…",
            doneText = "Ticket generado correctamente",
            failText = "Venta guardada. Error al imprimir ticket.",
            record = { comprobante ->
                c.register.receiveData(tipo, valor, comprobante)
                LastTransaction(tipo, valor, comprobante, sunday)
            },
            print = { last -> c.printer.printTicket(tipo, valor, last.comprobante, sunday) },
        )
    }

    /** Oferta en ruta: líneas cantidad × valor, un solo comprobante y un solo movimiento en la caja. */
    fun sellOffer(entries: List<OfferEntry>): String? {
        val ok = when (val r = Offers.validate(entries)) {
            is Offers.Result.Invalid -> return r.reason
            is Offers.Result.Ok -> r
        }
        val sunday = sundayRate.value
        val total = ok.total.toDouble()
        launchSale(
            announce = "Generando oferta…",
            doneText = "Oferta generada correctamente",
            failText = "Oferta guardada. Error al imprimir ticket.",
            record = { comprobante ->
                c.register.addOffer(total, comprobante)
                LastTransaction(OFFER_NAME, total, comprobante, sunday, tipo = "ofertaMultiple", offerEntries = ok.entries)
            },
            print = { last -> c.printer.printOffer(ok.entries, last.comprobante) },
        )
        return null
    }

    /** Cargo: un comprobante, dos copias (cliente y carga) impresas una tras otra. */
    fun sellCargo(data: CargoData) {
        val sunday = sundayRate.value
        launchSale(
            announce = "Generando cargo…",
            doneText = "Cargo generado correctamente",
            failText = "Cargo guardado. Error al imprimir ticket.",
            record = { comprobante ->
                c.register.receiveCargoData(data.destinatario, data.precio, comprobante)
                LastTransaction(
                    "$CARGO_PREFIX ${data.destinatario}", data.precio, comprobante, sunday,
                    destinatario = data.destinatario, articulo = data.articulo, destino = data.destino, telefono = data.telefono,
                )
            },
            print = { last -> c.printer.printCargo(data, last.comprobante, listOf(CargoCopy.Client, CargoCopy.Cargo)) },
        )
    }

    private fun launchSale(
        announce: String,
        doneText: String,
        failText: String,
        record: (comprobante: String) -> LastTransaction,
        print: suspend (LastTransaction) -> Unit,
    ) {
        if (busy.value) return
        // busy se activa antes de cualquier suspensión: el cierre automático suspende en Room y
        // un segundo toque en esa ventana generaría una segunda venta.
        busy.value = true
        viewModelScope.launch {
            try {
                when (c.closing.checkAndAutoClose()) {
                    AutoCloseResult.Closed ->
                        _messages.tryEmit(UiMessage("Se cerró la caja del día anterior automáticamente"))
                    is AutoCloseResult.Failed -> {
                        val date = c.prefs.lastTransactionDate?.let(LocalDay::parse)?.let {
                            "%02d/%02d/%04d".format(it.day, it.month, it.year)
                        } ?: "día anterior"
                        _messages.tryEmit(
                            UiMessage("No se pudo cerrar la caja del $date. Ciérrela manualmente desde el menú.", MessageKind.Error),
                        )
                    }
                    AutoCloseResult.NotNeeded -> Unit
                }
                if (c.pending.hasPreviousDayTransactions()) {
                    _pendingAlert.tryEmit(c.pending.pendingDays())
                    return@launch
                }
                hasReprinted.value = false
                hasCancelled.value = false
                _messages.tryEmit(UiMessage(announce))

                // Comprobante, caja, fecha y última venta se guardan de una vez (un solo commit): si el
                // equipo se apaga justo después, la venta y su comprobante quedan siempre consistentes.
                val last = runCatchingCancellable {
                    recordSale(c.prefs, c.comprobante, c.register) { comprobante ->
                        record(comprobante).also {
                            c.closing.updateLastTransactionDate()
                            c.prefs.lastTransaction = it
                        }
                    }
                }.getOrElse {
                    _messages.tryEmit(UiMessage("Error al guardar venta. Contacte soporte.", MessageKind.Error))
                    return@launch
                }
                lastTransaction.value = last

                // La impresión es independiente: un fallo no revierte la venta.
                runCatchingCancellable { print(last) }
                    .onSuccess { _messages.tryEmit(UiMessage(doneText, MessageKind.Success)) }
                    .onFailure { _messages.tryEmit(UiMessage(failText, MessageKind.Warning)) }
            } finally {
                busy.value = false
            }
        }
    }

    /** Hasta portar SumUp y las pantallas de oferta/cargo, estos botones avisan en vez de fallar en silencio. */
    fun notifyCardPending() { _messages.tryEmit(UiMessage("Pago con tarjeta (SumUp): disponible en la fase de integraciones", MessageKind.Warning)) }

    fun verifyPassword(input: String) = c.passwords.verifyLogin(input)

    /** Anula la última venta (la caja agrega la línea negativa y el contador retrocede 1). */
    fun cancelLast() {
        viewModelScope.launch {
            runCatchingCancellable { cancelLastSale(c.prefs, c.comprobante, c.register) }
                .onSuccess {
                    if (it) {
                        hasCancelled.value = true
                        _messages.tryEmit(UiMessage("Última venta anulada."))
                    }
                }
                .onFailure { _messages.tryEmit(UiMessage("Error al anular la venta. Contacte soporte.", MessageKind.Error)) }
        }
    }

    /**
     * Reimprime la última venta con su comprobante original. Ticket normal y oferta: una sola vez;
     * cargo: las veces que haga falta, eligiendo [copies].
     */
    fun reprintLast(copies: List<CargoCopy> = listOf(CargoCopy.Client, CargoCopy.Cargo)) {
        val last = lastTransaction.value
        if (last == null) {
            _messages.tryEmit(UiMessage("No hay transacción para reimprimir", MessageKind.Warning))
            return
        }
        if (busy.value) return
        if (!last.isCargo && hasReprinted.value) {
            _messages.tryEmit(UiMessage("Ya se ha reimpreso este boleto. Genere uno nuevo para reimprimir.", MessageKind.Warning))
            return
        }
        val cargo = last.cargoData()
        if (last.isCargo && cargo == null) {
            _messages.tryEmit(UiMessage("No hay suficientes detalles para reimprimir", MessageKind.Warning))
            return
        }
        val offer = last.offerEntries
        if (last.isOffer && offer.isNullOrEmpty()) {
            _messages.tryEmit(UiMessage("No hay suficientes detalles para reimprimir", MessageKind.Warning))
            return
        }
        viewModelScope.launch {
            busy.value = true
            runCatchingCancellable {
                when {
                    cargo != null -> c.printer.printCargo(cargo, last.comprobante, copies, reprint = true)
                    last.isOffer -> c.printer.printOffer(offer.orEmpty(), last.comprobante, reprint = true)
                    else -> c.printer.printTicket(last.nombre, last.valor, last.comprobante, last.switchValue, reprint = true)
                }
            }
                .onSuccess { if (!last.isCargo) hasReprinted.value = true; _messages.tryEmit(UiMessage("Ticket reimpreso", MessageKind.Success)) }
                .onFailure { _messages.tryEmit(UiMessage("Error al reimprimir: ${it.message}", MessageKind.Error)) }
            busy.value = false
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
