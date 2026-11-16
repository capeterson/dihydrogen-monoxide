package casa.peterson.dhmo.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.usecase.goal.SetGlobalOverrideUseCase
import casa.peterson.dhmo.core.model.settings.GlobalOverride
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class GlobalOverrideUiState(
    val loading: Boolean = true,
    val enabled: Boolean = false,
    val target: VolumeField = VolumeField("", null),
    /** One field per weekday, Monday first; an empty field means "use [target]". */
    val perWeekday: Map<DayOfWeek, VolumeField> = DayOfWeek.entries.associateWith { VolumeField("", null) },
    val units: VolumeUnit = VolumeUnit.ML,
    val invalid: Boolean = false,
    val saving: Boolean = false,
)

@HiltViewModel
internal class GlobalOverrideViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val setGlobalOverride: SetGlobalOverrideUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(GlobalOverrideUiState())
    val state: StateFlow<GlobalOverrideUiState> = mutableState.asStateFlow()
    private val savedChannel = Channel<Unit>(Channel.BUFFERED)

    /** Emits once the override has been written; the screen then closes. */
    val saved: Flow<Unit> = savedChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val current = settings.current()
            val override = current.goal.globalOverride
            val units = current.units
            mutableState.value = GlobalOverrideUiState(
                loading = false,
                enabled = override.enabled,
                target = VolumeField.of(override.targetMl, units),
                perWeekday = DayOfWeek.entries.associateWith { VolumeField.of(override.perWeekdayMl[it.name], units) },
                units = units,
            )
        }
    }

    fun onEnabledChange(enabled: Boolean) = mutableState.update { it.copy(enabled = enabled, invalid = false) }

    fun onTargetChange(text: String) = mutableState.update { it.copy(target = it.target.copy(text = text), invalid = false) }

    fun onWeekdayChange(day: DayOfWeek, text: String) = mutableState.update { state ->
        val field = state.perWeekday.getValue(day)
        state.copy(perWeekday = state.perWeekday + (day to field.copy(text = text)), invalid = false)
    }

    fun onSave() {
        val state = mutableState.value
        if (state.loading || state.saving) return
        val override = parse(state)
        if (override == null) {
            mutableState.update { it.copy(invalid = true) }
            return
        }
        mutableState.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                setGlobalOverride(override)
                savedChannel.send(Unit)
            } catch (_: IllegalArgumentException) {
                mutableState.update { it.copy(invalid = true) }
            } finally {
                mutableState.update { it.copy(saving = false) }
            }
        }
    }

    /** The override to save, or null when any value is missing or outside 250–10,000 ml. */
    private fun parse(state: GlobalOverrideUiState): GlobalOverride? {
        fun VolumeField.valid(): Int? = toMl(state.units)?.takeIf { it in MANUAL_TARGET_RANGE }
        val target = state.target.valid()
        val filled = state.perWeekday.filterValues { it.text.isNotBlank() }
        val perWeekday = filled.entries.mapNotNull { (day, field) -> field.valid()?.let { day.name to it } }.toMap()
        return if (target == null || perWeekday.size != filled.size) {
            null
        } else {
            GlobalOverride(enabled = state.enabled, targetMl = target, perWeekdayMl = perWeekday)
        }
    }
}
