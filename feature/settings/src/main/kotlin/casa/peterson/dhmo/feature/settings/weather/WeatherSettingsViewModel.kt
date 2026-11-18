package casa.peterson.dhmo.feature.settings.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.data.repository.DeviceLocationSource
import casa.peterson.dhmo.core.data.repository.DeviceStateRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.repository.WeatherController
import casa.peterson.dhmo.core.model.settings.ManualCity
import casa.peterson.dhmo.core.model.settings.WeatherMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class WeatherUiState(
    val mode: WeatherMode = WeatherMode.OFF,
    val city: ManualCity? = null,
    val lastFetchFailed: Boolean = false,
    val deviceSupported: Boolean = true,
    val permissionDenied: Boolean = false,
    val refreshing: Boolean = false,
)

@HiltViewModel
internal class WeatherSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    deviceState: DeviceStateRepository,
    private val controller: WeatherController,
    private val location: DeviceLocationSource,
) : ViewModel() {
    private val transient = MutableStateFlow(WeatherUiState(deviceSupported = location.isSupported()))

    val state: StateFlow<WeatherUiState> = combine(settings.settings, deviceState.state, transient) { s, d, t ->
        t.copy(mode = s.weather.mode, city = s.weather.manualCity, lastFetchFailed = d.weatherRuntime.lastFetchFailed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeatherUiState())

    fun hasLocationPermission(): Boolean = location.hasPermission()

    fun setOff() = setMode(WeatherMode.OFF)

    /** Device mode needs the permission first; the screen asks for it and calls [onLocationPermissionResult]. */
    fun onLocationPermissionResult(granted: Boolean) {
        if (granted) {
            transient.update { it.copy(permissionDenied = false) }
            setMode(WeatherMode.DEVICE_LOCATION)
        } else {
            transient.update { it.copy(permissionDenied = true) }
        }
    }

    /** City mode with no city yet opens the search instead; the search sets the mode and the city together. */
    fun useCity(openSearch: () -> Unit) {
        viewModelScope.launch {
            if (settings.current().weather.manualCity == null) openSearch() else setModeNow(WeatherMode.MANUAL_CITY)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            transient.update { it.copy(refreshing = true) }
            if (state.value.mode == WeatherMode.DEVICE_LOCATION) location.refresh()
            controller.refreshNow()
            transient.update { it.copy(refreshing = false) }
        }
    }

    private fun setMode(mode: WeatherMode) {
        viewModelScope.launch { setModeNow(mode) }
    }

    private suspend fun setModeNow(mode: WeatherMode) {
        settings.update { it.copy(weather = it.weather.copy(mode = mode)) }
        controller.onSettingsChanged()
    }
}
