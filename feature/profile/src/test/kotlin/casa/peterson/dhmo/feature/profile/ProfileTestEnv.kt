package casa.peterson.dhmo.feature.profile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import casa.peterson.dhmo.core.data.time.DayTicker
import casa.peterson.dhmo.core.data.usecase.goal.BuildGoalInputUseCase
import casa.peterson.dhmo.core.data.usecase.goal.ClearDayOverrideUseCase
import casa.peterson.dhmo.core.data.usecase.goal.SaveProfileUseCase
import casa.peterson.dhmo.core.data.usecase.goal.SetDayOverrideUseCase
import casa.peterson.dhmo.core.data.usecase.goal.SetGlobalOverrideUseCase
import casa.peterson.dhmo.core.data.usecase.goal.SnapshotDailyGoalUseCase
import casa.peterson.dhmo.core.domain.goal.GoalCalculator
import casa.peterson.dhmo.core.domain.goal.GoalPolicyV1
import casa.peterson.dhmo.core.model.Profile
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.testing.FakeActivityRepository
import casa.peterson.dhmo.core.testing.FakeGoalRepository
import casa.peterson.dhmo.core.testing.FakeHealthConnectController
import casa.peterson.dhmo.core.testing.FakeProfileRepository
import casa.peterson.dhmo.core.testing.FakeReminderScheduler
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.FakeWeatherRepository
import casa.peterson.dhmo.core.testing.FixedZoneProvider
import casa.peterson.dhmo.core.testing.MutableClock
import casa.peterson.dhmo.core.testing.TestData
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Real goal use cases over the `:core:testing` fakes, on Wednesday 2026-06-10 at noon UTC. */
internal class ProfileTestEnv(profile: Profile? = TestData.adultProfile, settings: AppSettings = AppSettings()) {
    val today: LocalDate = LocalDate.of(2026, 6, 10)
    val clock = MutableClock(Instant.parse("2026-06-10T12:00:00Z"))
    val zone = FixedZoneProvider(ZoneOffset.UTC)
    val settings = FakeSettingsRepository(settings)
    val profiles = FakeProfileRepository(listOfNotNull(profile?.let { TestData.snapshot(LocalDate.of(2026, 1, 1), it) }), clock)
    val goals = FakeGoalRepository(clock)
    val activity = FakeActivityRepository(clock)
    val weather = FakeWeatherRepository()
    val reminders = FakeReminderScheduler()
    val ticker = DayTicker(ApplicationProvider.getApplicationContext<Context>(), clock, zone, this.settings)
    val snapshot: SnapshotDailyGoalUseCase = SnapshotDailyGoalUseCase(
        BuildGoalInputUseCase(profiles, this.settings, activity, weather, FakeHealthConnectController()),
        // Pinned to v1: these tests check wiring against the TECHNICAL_PLAN §7.5 worked examples.
        GoalCalculator(GoalPolicyV1),
        GoalPolicyV1,
        goals,
        this.settings,
        reminders,
        clock,
    )

    fun profileGoalViewModel() = ProfileGoalViewModel(
        ticker,
        profiles,
        goals,
        activity,
        weather,
        settings,
        snapshot,
        ClearDayOverrideUseCase(goals, ticker, snapshot),
    )

    fun globalOverrideViewModel() = GlobalOverrideViewModel(settings, SetGlobalOverrideUseCase(settings, ticker, snapshot))

    fun adjustmentsViewModel() = AdjustmentsViewModel(settings, ticker, snapshot)

    fun editProfileViewModel() = EditProfileViewModel(profiles, ticker, SaveProfileUseCase(profiles, ticker, snapshot), settings)
}
