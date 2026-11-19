package casa.peterson.dhmo.core.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import casa.peterson.dhmo.core.data.usecase.goal.BuildGoalInputUseCase
import casa.peterson.dhmo.core.data.usecase.goal.SnapshotDailyGoalUseCase
import casa.peterson.dhmo.core.data.usecase.intake.LogIntakeUseCase
import casa.peterson.dhmo.core.domain.goal.GoalCalculator
import casa.peterson.dhmo.core.domain.goal.GoalPolicyV1
import casa.peterson.dhmo.core.domain.pace.BusyInterval
import casa.peterson.dhmo.core.model.Source
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.settings.GlobalOverride
import casa.peterson.dhmo.core.model.settings.GoalSettings
import casa.peterson.dhmo.core.model.settings.ReminderSettings
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.core.testing.FakeActivityRepository
import casa.peterson.dhmo.core.testing.FakeContainerRepository
import casa.peterson.dhmo.core.testing.FakeDeviceStateRepository
import casa.peterson.dhmo.core.testing.FakeGoalRepository
import casa.peterson.dhmo.core.testing.FakeHealthConnectController
import casa.peterson.dhmo.core.testing.FakeIntakeRepository
import casa.peterson.dhmo.core.testing.FakeProfileRepository
import casa.peterson.dhmo.core.testing.FakeReminderScheduler
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.FakeWeatherRepository
import casa.peterson.dhmo.core.testing.FixedZoneProvider
import casa.peterson.dhmo.core.testing.MutableClock
import casa.peterson.dhmo.core.testing.TestData
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Provider
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/**
 * The reminder engine over the `:core:testing` fakes, in Europe/Berlin on 2026-06-10 (the §8.5 table): reminders on,
 * a fixed 2,000 ml goal (global override), default curve, 07:00–23:00, notifications granted.
 */
internal class ReminderTestEnv(reminders: ReminderSettings = ReminderSettings(enabled = true), at: String = "12:00") {
    val zone: ZoneId = ZoneId.of("Europe/Berlin")
    val day: LocalDate = LocalDate.of(2026, 6, 10)
    val app: Application = ApplicationProvider.getApplicationContext()
    val clock = MutableClock(local(at))
    val zoneProvider = FixedZoneProvider(zone)
    val settings = FakeSettingsRepository(
        AppSettings(
            units = VolumeUnit.ML,
            reminders = reminders,
            goal = GoalSettings(globalOverride = GlobalOverride(enabled = true, targetMl = 2000)),
        ),
    )
    val device = FakeDeviceStateRepository()
    val intake = FakeIntakeRepository(clock, zoneProvider)
    val goals = FakeGoalRepository(clock)
    val containers = FakeContainerRepository(TestData.defaultContainers(VolumeUnit.ML))
    var busy: List<BusyInterval> = emptyList()
    val calendarRequests = mutableListOf<Pair<Instant, Instant>>()
    var rolloverCount = 0
    val snapshot = SnapshotDailyGoalUseCase(
        BuildGoalInputUseCase(
            FakeProfileRepository(listOf(TestData.snapshot(LocalDate.of(2026, 1, 1))), clock),
            settings,
            FakeActivityRepository(clock),
            FakeWeatherRepository(),
            FakeHealthConnectController(),
        ),
        // Pinned to v1: these tests check wiring against the TECHNICAL_PLAN §7.5 worked examples.
        GoalCalculator(GoalPolicyV1),
        GoalPolicyV1,
        goals,
        settings,
        FakeReminderScheduler(),
        clock,
    )
    val buildState = BuildReminderStateUseCase(
        settings,
        device,
        goals,
        intake,
        { from, to ->
            calendarRequests += from to to
            busy
        },
        zoneProvider,
    )
    val notifier = ReminderNotifier(app)
    val scheduler = AlarmReminderScheduler(
        app,
        settings,
        device,
        containers,
        buildState,
        Provider { snapshot },
        notifier,
        { rolloverCount++ },
        clock,
        zoneProvider,
    )
    val actions = ReminderActionHandler(containers, LogIntakeUseCase(intake), scheduler, notifier)

    val alarms: ShadowAlarmManager get() = shadowOf(app.getSystemService(AlarmManager::class.java))
    val notifications get() = shadowOf(app.getSystemService(NotificationManager::class.java))

    init {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifier.ensureChannels()
    }

    fun local(time: String, date: LocalDate = LocalDate.of(2026, 6, 10)): Instant =
        LocalDateTime.of(date, java.time.LocalTime.parse(time)).atZone(ZoneId.of("Europe/Berlin")).toInstant()

    suspend fun drink(time: String, volumeMl: Int) {
        intake.add(volumeMl, Source.MANUAL, local(time))
    }
}
