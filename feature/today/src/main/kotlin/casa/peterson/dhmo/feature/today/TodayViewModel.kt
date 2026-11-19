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
import casa.peterson.dhmo.core.model.settings.ThemeMode
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
        val sipEvent: Long = 0L,
        val goalEvent: Long = 0L,
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
    private val observeToday: ObserveTodayUseCase,
    containers: ContainerRepository,
    private val settings: SettingsRepository,
    private val deviceState: DeviceStateRepository,
    private val logIntake: LogIntakeUseCase,
    private val deleteIntake: DeleteIntakeUseCase,
    private val undoDelete: UndoDeleteIntakeUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val effectChannel = Channel<TodayEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()
    private val logMutex = Mutex()
    private val motion = MutableStateFlow(TodayMotionCue())
    val uiState: StateFlow<TodayUiState> = combine(
        observeToday(),
        containers.observeAll(),
        settings.settings,
        deviceState.state,
        motion,
    ) { today, presets, current, device, cue ->
        val goal = today.goal
        if (goal == null) {
            TodayUiState.Loading
        } else {
            val held = cue.heldState?.takeIf { it.day == today.day }
            val recent = clock.instant().minus(Duration.ofDays(7)).toEpochMilli()
            TodayUiState.Ready(
                day = today.day,
                consumedMl = held?.consumedMl ?: today.totalMl,
                goalMl = goal.targetMl,
                calculatedMl = goal.calculatedMl,
                goalMode = goal.mode,
                pace = held?.pace ?: today.pace,
                containers = presets,
                entries = held?.entries ?: today.entries.sortedByDescending { it.instant },
                units = current.units,
                showBatteryHint = !device.reminderRuntime.batteryHintDismissed &&
                    device.reminderRuntime.missedAlarmTimesMs.count { it >= recent } >= 3,
                showDisclaimerNote = device.onboarding.manualModeRecommended,
                sipEvent = if (cue.day == today.day) cue.sip else 0L,
                goalEvent = if (cue.day == today.day) cue.goal else 0L,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState.Loading)

    fun onQuickAdd(container: Container) = onCustomAdd(container.volumeMl)

    fun setNightMode(night: Boolean) {
        viewModelScope.launch {
            settings.update { it.copy(theme = it.theme.copy(mode = if (night) ThemeMode.DARK else ThemeMode.LIGHT)) }
        }
    }

    fun onCustomAdd(volumeMl: Int, at: Instant? = null) {
        viewModelScope.launch {
            try {
                // Serialize taps so rapid successful logs cannot race the once-per-day celebration marker.
                logMutex.withLock {
                    val before = uiState.value as? TodayUiState.Ready
                    motion.value = motion.value.copy(heldState = before)
                    try {
                        val entry = logIntake(volumeMl, Source.MANUAL, at)
                        if (before != null && entry.hydrationDay == before.day) {
                            signalConfirmedLog(entry, before)
                        }
                    } finally {
                        motion.value = motion.value.copy(heldState = null)
                    }
                }
            } catch (_: IllegalArgumentException) {
                effectChannel.send(TodayEffect.Error(R.string.feature_today_invalid_amount))
            }
        }
    }

    private suspend fun signalConfirmedLog(entry: IntakeEntry, before: TodayUiState.Ready) {
        // Entries and the SQL total arrive on separate flows. Wait for a consistent committed snapshot;
        // tapping a button, rejecting an amount, or editing a goal is never a successful sip.
        val confirmed = observeToday().first { snapshot ->
            snapshot.day != entry.hydrationDay ||
                (snapshot.entries.any { it.id == entry.id } && snapshot.totalMl == snapshot.entries.sumOf { it.volumeMl })
        }
        if (confirmed.day != entry.hydrationDay) return
        val goal = confirmed.goal?.targetMl ?: return
        var celebrate = false
        if (goal == before.goalMl && before.consumedMl < goal && confirmed.totalMl >= goal) {
            deviceState.update { device ->
                if (device.lastCelebratedGoalDay == confirmed.day.toEpochDay()) {
                    device
                } else {
                    celebrate = true
                    device.copy(lastCelebratedGoalDay = confirmed.day.toEpochDay())
                }
            }
        }
        val previous = motion.value
        val next = previous.sip + 1L
        motion.value = TodayMotionCue(
            day = confirmed.day,
            sip = next,
            goal = if (celebrate) {
                next
            } else if (previous.day == confirmed.day) {
                previous.goal
            } else {
                0L
            },
        )
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

private data class TodayMotionCue(
    val day: LocalDate? = null,
    val sip: Long = 0L,
    val goal: Long = 0L,
    /** Keep the jar still until the committed amount and its animation cue can be delivered together. */
    val heldState: TodayUiState.Ready? = null,
)
