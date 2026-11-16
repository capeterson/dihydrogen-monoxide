package casa.peterson.dhmo.feature.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.data.repository.GoalRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.usecase.goal.ClearDayOverrideUseCase
import casa.peterson.dhmo.core.data.usecase.goal.SetDayOverrideUseCase
import casa.peterson.dhmo.core.domain.goal.GoalResolver
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.feature.profile.navigation.DayOverrideRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class DayOverrideUiState(
    val loading: Boolean = true,
    val day: LocalDate? = null,
    /** The goal this day would have without an override: calculated or fixed. Null when unknown. */
    val withoutOverrideMl: Int? = null,
    val hasOverride: Boolean = false,
    val target: VolumeField = VolumeField("", null),
    val reason: String = "",
    val units: VolumeUnit = VolumeUnit.ML,
    val invalid: Boolean = false,
    val saving: Boolean = false,
)

@HiltViewModel
internal class DayOverrideViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val goals: GoalRepository,
    private val settings: SettingsRepository,
    private val setDayOverride: SetDayOverrideUseCase,
    private val clearDayOverride: ClearDayOverrideUseCase,
) : ViewModel() {
    private val day = DayOverrideRoute(savedState.get<Long>("epochDay") ?: Long.MIN_VALUE).date
    private val mutableState = MutableStateFlow(DayOverrideUiState(day = day))
    val state: StateFlow<DayOverrideUiState> = mutableState.asStateFlow()
    private val doneChannel = Channel<Unit>(Channel.BUFFERED)

    /** Emits after a save or a clear; the screen then closes. */
    val done: Flow<Unit> = doneChannel.receiveAsFlow()

    init {
        if (day != null) {
            viewModelScope.launch {
                val current = settings.current()
                val units = current.units
                val existing = goals.observeDayOverride(day).first()
                val goal = goals.goal(day)
                val withoutOverride = goal?.let {
                    val breakdown = it.breakdown.takeIf { _ -> it.calculatedMl != null }
                    GoalResolver.resolve(day, breakdown, null, current.goal.globalOverride).targetMl
                }
                mutableState.value = DayOverrideUiState(
                    loading = false,
                    day = day,
                    withoutOverrideMl = withoutOverride,
                    hasOverride = existing != null,
                    target = VolumeField.of(existing?.targetMl ?: goal?.targetMl, units),
                    reason = existing?.reason.orEmpty(),
                    units = units,
                )
            }
        }
    }

    fun onTargetChange(text: String) = mutableState.update { it.copy(target = it.target.copy(text = text), invalid = false) }

    fun onReasonChange(text: String) = mutableState.update { it.copy(reason = text) }

    fun onSave() {
        val state = mutableState.value
        val day = state.day
        if (day == null || state.loading || state.saving) return
        val target = state.target.toMl(state.units)?.takeIf { it in MANUAL_TARGET_RANGE }
        if (target == null) {
            mutableState.update { it.copy(invalid = true) }
            return
        }
        mutableState.update { it.copy(saving = true) }
        viewModelScope.launch {
            setDayOverride(day, target, state.reason.trim().ifEmpty { null })
            doneChannel.send(Unit)
        }
    }

    fun onClear() {
        val day = mutableState.value.day ?: return
        viewModelScope.launch {
            clearDayOverride(day)
            doneChannel.send(Unit)
        }
    }
}
