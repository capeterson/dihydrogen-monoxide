package casa.peterson.dhmo.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.data.repository.ContainerRepository
import casa.peterson.dhmo.core.data.repository.DeviceStateRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.usecase.intake.DeleteIntakeUseCase
import casa.peterson.dhmo.core.data.usecase.intake.LogIntakeUseCase
import casa.peterson.dhmo.core.data.usecase.intake.UndoDeleteIntakeUseCase
import casa.peterson.dhmo.core.data.usecase.today.ObserveTodayUseCase
import casa.peterson.dhmo.core.domain.pace.Pace
import casa.peterson.dhmo.core.model.Container
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.model.IntakeEntry
import casa.peterson.dhmo.core.model.Source
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data class Ready(
        val day: LocalDate,
        val consumedMl: Int,
        val goalMl: Int,
        val calculatedMl: Int?,
        val goalMode: GoalMode,
        val pace: Pace?,
        val containers: List<Container>,
        val entries: List<IntakeEntry>,
        val units: VolumeUnit,
        val showBatteryHint: Boolean,
        val showDisclaimerNote: Boolean,
    ) : TodayUiState {
        val progress: Float get() = if (goalMl > 0) consumedMl.toFloat() / goalMl else 0f
    }
}

sealed interface TodayEffect {
    data class EntryDeleted(val id: UUID, val volumeMl: Int? = null, val containerName: String? = null) : TodayEffect
    data class Error(val messageRes: Int) : TodayEffect
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    observeToday: ObserveTodayUseCase,
    containers: ContainerRepository,
    settings: SettingsRepository,
    deviceState: DeviceStateRepository,
    private val logIntake: LogIntakeUseCase,
    private val deleteIntake: DeleteIntakeUseCase,
    private val undoDelete: UndoDeleteIntakeUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val effectChannel = Channel<TodayEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()
    val uiState: StateFlow<TodayUiState> = combine(
        observeToday(),
        containers.observeAll(),
        settings.settings,
        deviceState.state,
    ) { today, presets, current, device ->
        val goal = today.goal
        if (goal == null) {
            TodayUiState.Loading
        } else {
            val recent = clock.instant().minus(Duration.ofDays(7)).toEpochMilli()
            TodayUiState.Ready(
                day = today.day,
                consumedMl = today.totalMl,
                goalMl = goal.targetMl,
                calculatedMl = goal.calculatedMl,
                goalMode = goal.mode,
                pace = today.pace,
                containers = presets,
                entries = today.entries.sortedByDescending { it.instant },
                units = current.units,
                showBatteryHint = !device.reminderRuntime.batteryHintDismissed &&
                    device.reminderRuntime.missedAlarmTimesMs.count { it >= recent } >= 3,
                showDisclaimerNote = device.onboarding.manualModeRecommended,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState.Loading)

    fun onQuickAdd(container: Container) = onCustomAdd(container.volumeMl)

    fun onCustomAdd(volumeMl: Int, at: Instant? = null) {
        viewModelScope.launch {
            try {
                logIntake(volumeMl, Source.MANUAL, at)
            } catch (_: IllegalArgumentException) {
                effectChannel.send(TodayEffect.Error(R.string.feature_today_invalid_amount))
            }
        }
    }

    fun onDelete(id: UUID) {
        val ready = uiState.value as? TodayUiState.Ready
        val volume = ready?.entries?.firstOrNull { it.id == id }?.volumeMl
        val name = ready?.containers?.firstOrNull { it.volumeMl == volume }?.name
        viewModelScope.launch {
            try {
                deleteIntake(id)
                effectChannel.send(TodayEffect.EntryDeleted(id, volume, name))
            } catch (_: IllegalStateException) {
                effectChannel.send(TodayEffect.Error(R.string.feature_today_entry_not_editable))
            } catch (_: NoSuchElementException) {
                effectChannel.send(TodayEffect.Error(R.string.feature_today_entry_missing))
            }
        }
    }

    fun onUndoDelete(id: UUID) {
        viewModelScope.launch {
            try {
                undoDelete(id)
            } catch (_: IllegalStateException) {
                effectChannel.send(TodayEffect.Error(R.string.feature_today_entry_not_editable))
            } catch (_: NoSuchElementException) {
                effectChannel.send(TodayEffect.Error(R.string.feature_today_entry_missing))
            }
        }
    }
}
