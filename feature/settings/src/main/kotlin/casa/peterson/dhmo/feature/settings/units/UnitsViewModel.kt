package casa.peterson.dhmo.feature.settings.units

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.usecase.goal.ChangeDayStartUseCase
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class UnitsUiState(
    val loading: Boolean = true,
    val units: VolumeUnit = VolumeUnit.ML,
    val dayStartMinutes: Int = 0,
    /** A day-start change waiting for the user to confirm the transition-day behaviour. */
    val pendingDayStart: Int? = null,
    val changingDayStart: Boolean = false,
)

/** Units and day start. A day-start change is confirmed first, then applied through [ChangeDayStartUseCase]. */
@HiltViewModel
internal class UnitsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val changeDayStart: ChangeDayStartUseCase,
) : ViewModel() {
    private val pending = MutableStateFlow<Int?>(null)
    private val busy = MutableStateFlow(false)

    val uiState: StateFlow<UnitsUiState> =
        combine(settings.settings, pending, busy) { current, pendingMinutes, changing ->
            UnitsUiState(
                loading = false,
                units = current.units,
                dayStartMinutes = current.dayStartMinutes,
                pendingDayStart = pendingMinutes,
                changingDayStart = changing,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnitsUiState())

    fun setUnits(units: VolumeUnit) {
        viewModelScope.launch { settings.update { it.copy(units = units) } }
    }

    /** Asks for confirmation; picking the current value does nothing. */
    fun requestDayStart(minutes: Int) {
        require(minutes in 0 until MINUTES_PER_DAY)
        viewModelScope.launch {
            pending.value = minutes.takeIf { it != settings.current().dayStartMinutes }
        }
    }

    fun confirmDayStart() {
        val minutes = pending.value ?: return
        pending.value = null
        busy.value = true
        viewModelScope.launch {
            try {
                changeDayStart(minutes)
            } finally {
                busy.update { false }
            }
        }
    }

    fun dismissDayStart() {
        pending.value = null
    }

    private companion object {
        const val MINUTES_PER_DAY = 1_440
    }
}
