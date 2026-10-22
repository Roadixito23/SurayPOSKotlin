package cl.suray.posbus.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cl.suray.posbus.AppContainer
import cl.suray.posbus.domain.DaySales
import cl.suray.posbus.domain.LocalDay
import cl.suray.posbus.domain.SalesStats
import cl.suray.posbus.domain.Statistics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StatsUiState(
    val loading: Boolean = true,
    val stats: SalesStats = SalesStats(),
    val weekly: List<DaySales> = emptyList(),
    val failed: Boolean = false,
)

class StatsViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, failed = false) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val all = c.statistics.allTransactions()
                    val today = LocalDay.of(c.clock.nowMillis())
                    Statistics.sales(all, today, days = 30) to Statistics.weekly(all, today)
                }
            }.onSuccess { (stats, weekly) ->
                _state.value = StatsUiState(loading = false, stats = stats, weekly = weekly)
            }.onFailure {
                _state.value = StatsUiState(loading = false, failed = true)
            }
        }
    }

    class Factory(private val c: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = StatsViewModel(c) as T
    }
}
