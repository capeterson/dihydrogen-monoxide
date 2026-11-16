package casa.peterson.dhmo.feature.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.data.repository.DeviceStateRepository
import casa.peterson.dhmo.core.data.repository.ReminderScheduler
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.domain.pace.PaceCurve
import casa.peterson.dhmo.core.model.RescheduleReason
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.settings.DeviceState
import casa.peterson.dhmo.core.model.settings.PaceCurveSettings
import casa.peterson.dhmo.core.model.settings.PaceCurveType
import casa.peterson.dhmo.core.model.settings.ReminderSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal enum class WindowWarning { TRUNCATED_BY_DAY_START, TOO_SHORT }

internal data class RemindersUiState(
    val settings: ReminderSettings = ReminderSettings(),
    val dayStartMinutes: Int = 0,
    val nextReminderAtMs: Long? = null,
) {
    val warning: WindowWarning? get() = windowWarning(settings.wakeMinutes, settings.sleepMinutes, dayStartMinutes)
}

/** Uses local wall-clock minutes, matching the day-end clipping in wakeWindow. */
internal fun windowWarning(wake: Int, sleep: Int, dayStart: Int): WindowWarning? {
    val wakeFromStart = (wake - dayStart + MINUTES_PER_DAY) % MINUTES_PER_DAY
    val duration = (sleep - wake + MINUTES_PER_DAY - 1) % MINUTES_PER_DAY + 1
    val available = MINUTES_PER_DAY - wakeFromStart
    return when {
        duration > available -> WindowWarning.TRUNCATED_BY_DAY_START
        duration < SHORT_WINDOW_MINUTES -> WindowWarning.TOO_SHORT
        else -> null
    }
}

@HiltViewModel
internal class RemindersViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val deviceState: DeviceStateRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {
    val state = combine(settings.settings, deviceState.state, ::toUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersUiState())

    fun setEnabled(enabled: Boolean) = update { copy(enabled = enabled) }
    fun setWake(minutes: Int) {
        require(minutes in 0 until MINUTES_PER_DAY)
        update { copy(wakeMinutes = minutes) }
    }
    fun setSleep(minutes: Int) {
        require(minutes in 0 until MINUTES_PER_DAY)
        update { copy(sleepMinutes = minutes) }
    }
    fun setInterval(minutes: Int) {
        require(minutes in 15..240)
        update { copy(intervalFloorMinutes = minutes) }
    }
    fun setTolerance(percent: Int) {
        require(percent in 0..50)
        update { copy(tolerancePercent = percent) }
    }
    fun setEscalation(enabled: Boolean) = update { copy(escalationEnabled = enabled) }
    fun setSummary(enabled: Boolean) = update { copy(endOfDaySummaryEnabled = enabled) }
    fun setCalendar(enabled: Boolean) = update { copy(calendarBusyEnabled = enabled) }
    fun setCurve(curve: PaceCurveSettings) {
        require(curve.type != PaceCurveType.CUSTOM || PaceCurve.validate(curve.customPoints))
        update { copy(curve = curve) }
    }

    private fun update(transform: ReminderSettings.() -> ReminderSettings) {
        viewModelScope.launch {
            settings.update { it.copy(reminders = it.reminders.transform()) }
            scheduler.reschedule(RescheduleReason.SETTINGS_CHANGED)
        }
    }
}

private fun toUiState(settings: AppSettings, device: DeviceState) = RemindersUiState(
    settings = settings.reminders,
    dayStartMinutes = settings.dayStartMinutes,
    nextReminderAtMs = device.reminderRuntime.scheduledAtMs,
)

private const val MINUTES_PER_DAY = 1_440
private const val SHORT_WINDOW_MINUTES = 4 * 60
