package casa.peterson.dhmo.core.data.usecase.goal

import casa.peterson.dhmo.core.domain.goal.GoalCalculator
import casa.peterson.dhmo.core.domain.goal.GoalPolicyV1
import casa.peterson.dhmo.core.model.ActivityInput
import casa.peterson.dhmo.core.model.ClimateInput
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.model.Intensity
import casa.peterson.dhmo.core.model.RescheduleReason
import casa.peterson.dhmo.core.model.WeatherDay
import casa.peterson.dhmo.core.model.settings.GlobalOverride
import casa.peterson.dhmo.core.model.settings.WeatherMode
import casa.peterson.dhmo.core.testing.FakeActivityRepository
import casa.peterson.dhmo.core.testing.FakeGoalRepository
import casa.peterson.dhmo.core.testing.FakeHealthConnectController
import casa.peterson.dhmo.core.testing.FakeProfileRepository
import casa.peterson.dhmo.core.testing.FakeReminderScheduler
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.FakeWeatherRepository
import casa.peterson.dhmo.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SnapshotDailyGoalUseCaseTest {
    private val day = LocalDate.of(2026, 6, 10)
    private val clock = Clock.fixed(Instant.parse("2026-06-10T12:00:00Z"), ZoneOffset.UTC)
    private val profiles: FakeProfileRepository = FakeProfileRepository(listOf(TestData.snapshot(day)))
    private val settings = FakeSettingsRepository()
    private val goals = FakeGoalRepository(clock)
    private val reminders = FakeReminderScheduler()
    private val activity = FakeActivityRepository(clock)
    private val weather = FakeWeatherRepository()
    private val input: BuildGoalInputUseCase = BuildGoalInputUseCase(
        profiles,
        settings,
        activity,
        weather,
        FakeHealthConnectController(),
    )
    private val snapshot = SnapshotDailyGoalUseCase(input, GoalCalculator(), GoalPolicyV1, goals, settings, reminders, clock)

    @Test
    fun unchangedSnapshotIsNotWrittenOrRescheduled() = runTest {
        val first = snapshot(day)
        assertThat(snapshot(day)).isEqualTo(first)
        assertThat(goals.savedSnapshots).hasSize(1)
        assertThat(first.policyVersion).isEqualTo(GoalPolicyV1.version)
        assertThat(first.breakdown.components).isNotEmpty()
        assertThat(reminders.reasons).containsExactly(RescheduleReason.GOAL_CHANGED)
    }

    @Test
    fun overridesAndDayBoundaryArePersisted() = runTest {
        settings.update { it.copy(dayStartMinutes = 240, goal = it.goal.copy(globalOverride = GlobalOverride(true, 1800))) }
        val global = snapshot(day)
        assertThat(global.mode).isEqualTo(GoalMode.GLOBAL_OVERRIDE)
        assertThat(global.targetMl).isEqualTo(1800)
        assertThat(global.dayStartMinutes).isEqualTo(240)
        goals.setDayOverride(day, 2100, null)
        val daily = snapshot(day)
        assertThat(daily.mode).isEqualTo(GoalMode.DAY_OVERRIDE)
        assertThat(daily.targetMl).isEqualTo(2100)
        assertThat(reminders.reasons).hasSize(2)
    }

    @Test
    fun forecastFallbackAndPinnedActivityAreResolved() = runTest {
        settings.update { it.copy(weather = it.weather.copy(mode = WeatherMode.MANUAL_CITY)) }
        activity.addPlanned(day, 30, Intensity.MODERATE)
        val missing = checkNotNull(input(day))
        assertThat((missing.climate as ClimateInput.Manual).forecastUnavailable).isTrue()
        assertThat(missing.activity).isInstanceOf(ActivityInput.Sessions::class.java)
        weather.save(WeatherDay(day, 0, 0, 30.0, 32.0, null, 500, clock.instant()))
        settings.update { it.copy(goal = it.goal.copy(pinned = it.goal.pinned.copy(pinActivityLevel = true))) }
        val resolved = checkNotNull(input(day))
        assertThat((resolved.climate as ClimateInput.Forecast).maxApparentTempC).isEqualTo(32.0)
        assertThat(resolved.activity).isInstanceOf(ActivityInput.Level::class.java)
    }

    @Test
    fun changingBreakdownWithoutChangingTargetDoesNotReschedule() = runTest {
        settings.update { it.copy(goal = it.goal.copy(globalOverride = GlobalOverride(true, 1800))) }
        snapshot(day)
        profiles.save(TestData.adultProfile.copy(weightKg = 80.0), day)
        snapshot(day)
        assertThat(goals.savedSnapshots).hasSize(2)
        assertThat(reminders.reasons).hasSize(1)
    }
}
