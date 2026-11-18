package casa.peterson.dhmo.feature.settings.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.data.repository.GeoPlace
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.repository.WeatherController
import casa.peterson.dhmo.core.data.repository.WeatherProvider
import casa.peterson.dhmo.core.data.repository.WeatherResult
import casa.peterson.dhmo.core.model.settings.ManualCity
import casa.peterson.dhmo.core.model.settings.WeatherMode
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal sealed interface CitySearchState {
    data object Idle : CitySearchState

    data object Searching : CitySearchState

    data object Failed : CitySearchState

    data class Results(val places: List<GeoPlace>) : CitySearchState
}

@HiltViewModel
internal class CitySearchViewModel @Inject constructor(
    private val provider: WeatherProvider,
    private val settings: SettingsRepository,
    private val controller: WeatherController,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query
    private val _state = MutableStateFlow<CitySearchState>(CitySearchState.Idle)
    val state: StateFlow<CitySearchState> = _state

    init {
        viewModelScope.launch { observeQuery() }
    }

    fun onQueryChange(text: String) {
        _query.value = text
    }

    /** Stores only the rounded coordinates, never the provider's exact ones. */
    fun choose(place: GeoPlace, onDone: () -> Unit) {
        viewModelScope.launch {
            val city = ManualCity(
                name = place.name,
                admin1 = place.admin1,
                countryCode = place.countryCode,
                latE1 = (place.latitude * 10).roundToInt(),
                lonE1 = (place.longitude * 10).roundToInt(),
            )
            settings.update { it.copy(weather = it.weather.copy(mode = WeatherMode.MANUAL_CITY, manualCity = city)) }
            controller.onSettingsChanged()
            onDone()
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private suspend fun observeQuery() {
        _query.debounce(DEBOUNCE_MS).distinctUntilChanged().collectLatest { text ->
            val trimmed = text.trim()
            if (trimmed.length < MIN_QUERY) {
                _state.value = CitySearchState.Idle
                return@collectLatest
            }
            _state.value = CitySearchState.Searching
            _state.value = when (val result = provider.searchCity(trimmed, Locale.getDefault().toLanguageTag())) {
                is WeatherResult.Success -> CitySearchState.Results(result.value)
                is WeatherResult.Failure -> CitySearchState.Failed
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 400L
        const val MIN_QUERY = 2
    }
}
