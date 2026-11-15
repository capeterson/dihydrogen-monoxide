package casa.peterson.dhmo.feature.reminders

import casa.peterson.dhmo.core.model.RescheduleReason
import casa.peterson.dhmo.core.model.settings.CurvePoint
import casa.peterson.dhmo.core.model.settings.PaceCurveSettings
import casa.peterson.dhmo.core.model.settings.PaceCurveType
import casa.peterson.dhmo.core.testing.FakeDeviceStateRepository
import casa.peterson.dhmo.core.testing.FakeReminderScheduler
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemindersViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val settings = FakeSettingsRepository()
    private val scheduler = FakeReminderScheduler()
    private val model by lazy { RemindersViewModel(settings, FakeDeviceStateRepository(), scheduler) }

    @Test
    fun changesPersistAndEachReschedules() = runTest {
        model.setEnabled(true)
        model.setWake(480)
        model.setSleep(1320)
        model.setInterval(30)
        model.setTolerance(20)
        model.setEscalation(true)
        model.setSummary(true)
        model.setCalendar(true)
        model.setCurve(PaceCurveSettings(type = PaceCurveType.LINEAR))
        advanceUntilIdle()
        val result = settings.current().reminders
        assertThat(result.enabled).isTrue()
        assertThat(result.wakeMinutes).isEqualTo(480)
        assertThat(result.sleepMinutes).isEqualTo(1320)
        assertThat(result.intervalFloorMinutes).isEqualTo(30)
        assertThat(result.tolerancePercent).isEqualTo(20)
        assertThat(result.escalationEnabled).isTrue()
        assertThat(result.endOfDaySummaryEnabled).isTrue()
        assertThat(result.calendarBusyEnabled).isTrue()
        assertThat(result.curve.type).isEqualTo(PaceCurveType.LINEAR)
        assertThat(scheduler.reasons).containsExactlyElementsIn(List(9) { RescheduleReason.SETTINGS_CHANGED })
    }

    @Test
    fun invalidInputsAreRejected() {
        assertThat(runCatching { model.setInterval(14) }.isFailure).isTrue()
        assertThat(runCatching { model.setTolerance(51) }.isFailure).isTrue()
        val invalid = PaceCurveSettings(PaceCurveType.CUSTOM, listOf(CurvePoint(0, 0), CurvePoint(500, 1000)))
        assertThat(runCatching { model.setCurve(invalid) }.isFailure).isTrue()
        assertThat(scheduler.reasons).isEmpty()
    }

    @Test
    fun warningsMatchHydrationDayClipping() {
        assertThat(windowWarning(7 * 60, 23 * 60, 0)).isNull()
        assertThat(windowWarning(9 * 60, 60, 0)).isEqualTo(WindowWarning.TRUNCATED_BY_DAY_START)
        assertThat(windowWarning(3 * 60, 20 * 60, 4 * 60)).isEqualTo(WindowWarning.TRUNCATED_BY_DAY_START)
        assertThat(windowWarning(9 * 60, 12 * 60, 0)).isEqualTo(WindowWarning.TOO_SHORT)
        assertThat(windowWarning(9 * 60, 60, 4 * 60)).isNull()
    }
}
