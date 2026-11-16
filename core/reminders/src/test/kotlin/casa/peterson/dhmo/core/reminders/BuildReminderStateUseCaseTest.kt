package casa.peterson.dhmo.core.reminders

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.domain.pace.BusyInterval
import casa.peterson.dhmo.core.model.settings.ReminderRuntime
import casa.peterson.dhmo.core.model.settings.ReminderSettings
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BuildReminderStateUseCaseTest {
    @Test
    fun assemblesTodaysState() = runTest {
        val env = ReminderTestEnv(
            ReminderSettings(enabled = true, wakeMinutes = 8 * 60, intervalFloorMinutes = 45, tolerancePercent = 5),
            at = "12:00",
        )
        env.snapshot(env.day)
        env.drink("08:30", 300)
        env.drink("10:15", 200)
        val state = checkNotNull(env.buildState(env.clock.instant()))
        assertThat(state.day).isEqualTo(env.day)
        assertThat(state.zone).isEqualTo(env.zone)
        assertThat(state.goalMl).isEqualTo(2000)
        assertThat(state.consumedMl).isEqualTo(500)
        assertThat(state.lastDrinkAt).isEqualTo(env.local("10:15"))
        assertThat(state.config.wake).isEqualTo(LocalTime.of(8, 0))
        assertThat(state.config.intervalFloor).isEqualTo(Duration.ofMinutes(45))
        assertThat(state.config.tolerancePercent).isEqualTo(5)
        assertThat(state.busy).isEmpty()
        assertThat(env.calendarRequests).isEmpty()
    }

    @Test
    fun runtimeFlagsApplyToTodayOnly() = runTest {
        val env = ReminderTestEnv(at = "12:00")
        env.snapshot(env.day)
        env.device.update {
            it.copy(
                reminderRuntime = ReminderRuntime(
                    lastReminderAtMs = env.local("11:00").toEpochMilli(),
                    snoozedUntilMs = env.local("11:30").toEpochMilli(),
                    escalationSentDay = env.day.toEpochDay(),
                    summarySentDay = env.day.minusDays(1).toEpochDay(),
                ),
            )
        }
        val state = checkNotNull(env.buildState(env.clock.instant()))
        assertThat(state.lastReminderAt).isEqualTo(env.local("11:00"))
        assertThat(state.snoozedUntil).isNull() // expired
        assertThat(state.escalationSentToday).isTrue()
        assertThat(state.summarySentToday).isFalse()
    }

    @Test
    fun readsTheCalendarOnlyWhenOptedInAndOverTheWakeWindow() = runTest {
        val env = ReminderTestEnv(ReminderSettings(enabled = true, calendarBusyEnabled = true), at = "12:00")
        env.snapshot(env.day)
        env.busy = listOf(BusyInterval(env.local("11:30"), env.local("12:45")))
        val state = checkNotNull(env.buildState(env.clock.instant()))
        assertThat(state.busy).isEqualTo(env.busy)
        assertThat(env.calendarRequests.single()).isEqualTo(env.local("07:00") to env.local("23:00"))
    }

    @Test
    fun theHydrationDayFollowsTheDayStart() = runTest {
        // 02:00 on June 11 with a 04:00 day start is still June 10.
        val env = ReminderTestEnv(at = "12:00")
        env.settings.update { it.copy(dayStartMinutes = 4 * 60) }
        env.clock.set(env.local("02:00", env.day.plusDays(1)))
        env.snapshot(env.day)
        assertThat(env.buildState(env.clock.instant())?.day).isEqualTo(env.day)
    }

    @Test
    fun noGoalSnapshotMeansNoState() = runTest {
        val env = ReminderTestEnv(at = "12:00")
        assertThat(env.buildState(env.clock.instant())).isNull()
    }
}
