package cl.suray.posbus.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cl.suray.posbus.AppContainer
import cl.suray.posbus.data.prefs.AppPrefs
import cl.suray.posbus.data.prefs.Tarifa
import cl.suray.posbus.domain.SettingsInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class SettingsUiState(
    val weekdayFares: List<Tarifa> = emptyList(),
    val sundayFares: List<Tarifa> = emptyList(),
    val terminalId: Int = 1,
    val comprobanteNumber: Int = 0,
    val sumUpEnabled: Boolean = true,
    val hasAffiliateKey: Boolean = false,
    val linkedCards: List<String> = emptyList(),
    val showIcons: Boolean = true,
    val buttonOpacity: Float = 1f,
    val textSize: Float = 0.8f,
    val pdfMargin: Float = AppPrefs.DEFAULT_PDF_END_MARGIN,
    val abbreviations: Map<String, String> = emptyMap(),
)

/** Resultado de una acción: `null` = correcto, texto = motivo del rechazo. */
typealias Rejection = String?

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<SettingsUiState> = _state

    private fun load() = SettingsUiState(
        weekdayFares = c.prefs.weekdayFares,
        sundayFares = c.prefs.sundayFares,
        terminalId = c.prefs.ticketId,
        comprobanteNumber = c.prefs.comprobanteNumber,
        sumUpEnabled = c.prefs.sumUpEnabled,
        hasAffiliateKey = !c.prefs.sumUpAffiliateKey.isNullOrBlank(),
        linkedCards = c.prefs.linkedNfcCards,
        showIcons = c.prefs.showIcons,
        buttonOpacity = c.prefs.buttonOpacity,
        textSize = c.prefs.textSizeMultiplier,
        pdfMargin = c.prefs.pdfEndMargin,
        abbreviations = AppPrefs.DEFAULT_ABBREVIATIONS.keys.associateWith { c.prefs.abbreviation(it) },
    )

    /** Relee todo y avisa a Home. */
    private fun changed() {
        _state.value = load()
        c.settingsRevision.update { it + 1 }
    }

    fun verifySettingsPassword(input: String) = c.passwords.verifySettings(input)
    fun verifyComprobantePassword(input: String) = c.passwords.verifyComprobante(input)

    // --- Tarifas ---

    fun saveFare(sunday: Boolean, index: Int, name: String, price: String): Rejection {
        val n = SettingsInput.fareName(name) ?: return "El nombre no puede estar vacío"
        val p = SettingsInput.price(price) ?: return "Precio inválido"
        val list = (if (sunday) c.prefs.sundayFares else c.prefs.weekdayFares).toMutableList()
        if (index !in list.indices) return "Tarifa inexistente"
        list[index] = Tarifa(n, p)
        if (sunday) c.prefs.sundayFares = list else c.prefs.weekdayFares = list
        changed()
        return null
    }

    fun restoreDefaultFares(sunday: Boolean) {
        if (sunday) c.prefs.sundayFares = AppPrefs.DEFAULT_SUNDAY_FARES else c.prefs.weekdayFares = AppPrefs.DEFAULT_WEEKDAY_FARES
        changed()
    }

    // --- General ---

    fun saveTerminalId(text: String): Rejection {
        val id = SettingsInput.terminalId(text) ?: return "Ingrese un ID válido (1-99)"
        c.prefs.ticketId = id
        changed()
        return null
    }

    fun resetComprobanteCounter() {
        c.comprobante.reset()
        changed()
    }

    fun changeAppPassword(new: String): Rejection =
        if (c.passwords.setPassword(new)) null else "La clave debe tener al menos 6 caracteres"

    fun changeSettingsPassword(new: String): Rejection =
        if (c.passwords.setSettingsPassword(new)) null else "La clave debe tener al menos 6 caracteres"

    fun changeComprobantePassword(new: String): Rejection =
        if (c.passwords.setComprobantePassword(new)) null else "La clave del comprobante debe tener 6 caracteres"

    fun setSumUpEnabled(value: Boolean) {
        c.prefs.sumUpEnabled = value
        changed()
    }

    fun saveAffiliateKey(text: String): Rejection {
        val key = SettingsInput.affiliateKey(text) ?: return "La clave de afiliado no puede estar vacía"
        c.prefs.sumUpAffiliateKey = key
        changed()
        return null
    }

    fun removeNfcCard(id: String) {
        c.prefs.linkedNfcCards = c.prefs.linkedNfcCards - id
        changed()
    }

    // --- Apariencia / PDF ---

    fun saveAppearance(showIcons: Boolean, opacity: Float, textSize: Float) {
        c.prefs.showIcons = showIcons
        c.prefs.buttonOpacity = opacity
        c.prefs.textSizeMultiplier = textSize
        changed()
    }

    fun savePdfMargin(text: String): Rejection {
        val m = SettingsInput.pdfMargin(text) ?: return "Margen inválido (0-${SettingsInput.MAX_PDF_MARGIN.toInt()})"
        c.prefs.pdfEndMargin = m
        changed()
        return null
    }

    fun saveAbbreviations(values: Map<String, String>) {
        values.forEach { (name, abbr) -> c.prefs.setAbbreviation(name, abbr.trim().ifEmpty { AppPrefs.DEFAULT_ABBREVIATIONS[name] ?: name }) }
        changed()
    }

    class Factory(private val c: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(c) as T
    }
}
