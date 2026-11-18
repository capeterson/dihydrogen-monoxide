package casa.peterson.dhmo.feature.insights.correlation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.time.DayTicker
import casa.peterson.dhmo.core.domain.stats.CorrelationResult
import casa.peterson.dhmo.core.domain.stats.correlate
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.feature.insights.domain.CorrelationDriver
import casa.peterson.dhmo.feature.insights.domain.LoadCorrelationPairsUseCase
import casa.peterson.dhmo.feature.insights.navigation.CorrelationRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal data class CorrelationUiState(
    val loading: Boolean = true,
    val driver: CorrelationDriver = CorrelationDriver.TEMPERATURE,
    val pairs: List<Pair<Double, Double>> = emptyList(),
    /** Null when there are fewer than 14 matching days or the values do not vary. */
    val result: CorrelationResult? = null,
    val unit: VolumeUnit = VolumeUnit.ML,
)

@HiltViewModel
internal class CorrelationViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val loadPairs: LoadCorrelationPairsUseCase,
    private val ticker: DayTicker,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val driver = CorrelationDriver.entries.firstOrNull { it.name == savedState.toRoute<CorrelationRoute>().driver }
        ?: CorrelationDriver.TEMPERATURE
    private val _state = MutableStateFlow(CorrelationUiState(driver = driver))
    val state: StateFlow<CorrelationUiState> = _state

    init {
        viewModelScope.launch {
            val pairs = loadPairs(driver, ticker.today().first())
            _state.value = CorrelationUiState(
                loading = false,
                driver = driver,
                pairs = pairs,
                result = correlate(pairs),
                unit = settings.current().units,
            )
        }
    }
}
