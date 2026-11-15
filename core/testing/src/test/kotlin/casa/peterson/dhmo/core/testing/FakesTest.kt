package casa.peterson.dhmo.core.testing

import app.cash.turbine.test
import casa.peterson.dhmo.core.data.repository.DailyForecast
import casa.peterson.dhmo.core.data.repository.WeatherResult
import casa.peterson.dhmo.core.model.ChangeOrigin
import casa.peterson.dhmo.core.model.Intensity
import casa.peterson.dhmo.core.model.RescheduleReason
import casa.peterson.dhmo.core.model.Source
import casa.peterson.dhmo.core.model.WeatherDay
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.core.model.sync.DriveBackupState
import casa.peterson.dhmo.core.model.sync.HcAvailability
import casa.peterson.dhmo.core.model.sync.HcSetting
import casa.peterson.dhmo.core.model.sync.HcSyncState
import casa.peterson.dhmo.core.model.sync.RemoteBackup
import casa.peterson.dhmo.core.model.sync.RestoreMode
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FakesTest {
    private val now = Instant.parse("2026-06-10T08:00:00Z")
    private val clock = MutableClock(now)
    private val day = LocalDate.of(2026, 6, 10)

    @Test
    fun `fake intake repository buckets, totals, deletes and rebuckets`() = runTest {
        val repo = FakeIntakeRepository(clock)
        val a = repo.add(250, Source.MANUAL)
        repo.add(500, Source.WIDGET, at = Instant.parse("2026-06-10T22:30:00Z")) // 00:30 on the 11th in Berlin
        assertThat(repo.dailyTotals(day, day.plusDays(1)).map { it.totalMl }).containsExactly(250, 500).inOrder()
        assertThat(repo.firstDay()).isEqualTo(day)

        repo.observeDailyTotal(day).test {
            assertThat(awaitItem()!!.totalMl).isEqualTo(250)
            repo.delete(a.id)
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
        repo.undoDelete(a.id)
        repo.update(a.id, 300, a.instant, "n")
        assertThat(repo.get(a.id)!!.volumeMl).isEqualTo(300)

        repo.rebucketFrom(day, LocalTime.of(4, 0))
        assertThat(repo.entries(day, day.plusDays(1))).hasSize(2)
        repo.notifyExternalChange(setOf(day), ChangeOrigin.RESTORE)
        assertThat(repo.externalChanges).containsExactly(ExternalChange(setOf(day), ChangeOrigin.RESTORE))
        assertThat(repo.observeRange(day, day).first()).hasSize(2) // 00:30 on the 11th now belongs to the 10th
    }

    @Test
    fun `fake container repository seeds, reorders and deletes`() = runTest {
        val repo = FakeContainerRepository()
        repo.seedDefaultsIfEmpty(VolumeUnit.US_FL_OZ)
        assertThat(repo.seededWith).isEqualTo(VolumeUnit.US_FL_OZ)
        assertThat(repo.getAll().map { it.volumeMl }).containsExactly(237, 473, 710).inOrder()
        repo.reorder(listOf(TestData.LARGE_BOTTLE_ID))
        assertThat(repo.observeAll().first().first().id).isEqualTo(TestData.LARGE_BOTTLE_ID)
        repo.delete(TestData.LARGE_BOTTLE_ID)
        assertThat(repo.getAll()).hasSize(2)
    }

    @Test
    fun `fake profile repository resolves by day`() = runTest {
        val repo = FakeProfileRepository(clock = clock)
        repo.save(TestData.adultProfile, LocalDate.of(2026, 1, 1))
        repo.addHealthConnectWeight(70.0, LocalDate.of(2026, 3, 1))
        assertThat(repo.profileFor(LocalDate.of(2026, 2, 1))!!.profile.weightKg).isEqualTo(65.0)
        assertThat(repo.observeCurrent().first()!!.weightKg).isEqualTo(70.0)
        assertThat(repo.observeSnapshots().first()).hasSize(2)
    }

    @Test
    fun `fake goal, activity, weather and settings repositories hold state`() = runTest {
        val goals = FakeGoalRepository(clock)
        goals.setDayOverride(day, 3000, "x")
        assertThat(goals.observeDayOverride(day).first()!!.targetMl).isEqualTo(3000)
        goals.clearDayOverride(day)
        assertThat(goals.observeDayOverride(day).first()).isNull()
        assertThat(goals.goal(day)).isNull()

        val activity = FakeActivityRepository(clock)
        activity.addPlanned(day, 20, Intensity.MODERATE)
        assertThat(activity.minutesPerDay(day, day)).containsExactly(day, 20)
        activity.removePlanned(activity.forDay(day).single().sourceRecordId)
        assertThat(activity.forDay(day)).isEmpty()

        val weather = FakeWeatherRepository()
        weather.save(WeatherDay(day, 1, 2, 20.0, 21.0, null, 0, now))
        assertThat(weather.forDay(day)!!.maxTempC).isEqualTo(20.0)
        assertThat(weather.range(day, day)).hasSize(1)

        val settings = FakeSettingsRepository()
        settings.update { it.copy(dayStartMinutes = 60) }
        assertThat(settings.current().dayStartMinutes).isEqualTo(60)
        val device = FakeDeviceStateRepository()
        device.update { it.copy(onboarding = it.onboarding.copy(completed = true)) }
        assertThat(device.current().onboarding.completed).isTrue()
    }

    @Test
    fun `fake backup repository records restores`() = runTest {
        val repo = FakeBackupRepository()
        assertThat(repo.latestSafetySnapshot()).isNull()
        assertThat(runCatching { repo.exportDocument() }.isFailure).isTrue()
        assertThat(repo.restores).isEmpty()
    }

    @Test
    fun `fake controllers record calls`() = runTest {
        val scheduler = FakeReminderScheduler()
        scheduler.reschedule(RescheduleReason.ENTRY_CHANGED)
        scheduler.snooze()
        scheduler.cancel()
        assertThat(scheduler.reasons).containsExactly(RescheduleReason.ENTRY_CHANGED)
        assertThat(scheduler.snoozeCount).isEqualTo(1)
        assertThat(scheduler.cancelCount).isEqualTo(1)

        val widget = FakeWidgetUpdater()
        widget.updateAll()
        assertThat(widget.updateCount).isEqualTo(1)

        val hc = FakeHealthConnectController(HcAvailability.AVAILABLE)
        hc.requestSync(true)
        hc.enqueueBackfill()
        hc.onSettingsChanged()
        assertThat(hc.availability()).isEqualTo(HcAvailability.AVAILABLE)
        assertThat(hc.permissionsFor(HcSetting.WRITE_HYDRATION)).isNotEmpty()
        assertThat(hc.syncRequests).containsExactly(true)
        assertThat(hc.backfillCount).isEqualTo(1)
        assertThat(hc.state.first()).isEqualTo(HcSyncState.Disabled)

        val drive = FakeDriveBackupController()
        val backup = RemoteBackup("1", "b.json", now, 10, false, 1, 3)
        drive.backups = listOf(backup)
        drive.backUpNow()
        assertThat(drive.listBackups().getOrThrow()).containsExactly(backup)
        drive.restore(backup, RestoreMode.MERGE, null)
        drive.disconnect()
        assertThat(drive.state.first()).isEqualTo(DriveBackupState.NotConfigured)
        assertThat(drive.restores).containsExactly(backup to RestoreMode.MERGE)
        assertThat(drive.backUpCount).isEqualTo(1)

        val provider = FakeWeatherProvider(forecast = WeatherResult.Success(DailyForecast(day, 30.0, 32.0, 50.0)))
        assertThat(provider.todayForecast(524, 134)).isInstanceOf(WeatherResult.Success::class.java)
        assertThat(provider.forecastRequests).containsExactly(524 to 134)
        val controller = FakeWeatherController()
        assertThat(controller.refreshNow()).isInstanceOf(WeatherResult.Failure::class.java)
        controller.refreshIfStale()
        assertThat(controller.refreshIfStaleCount).isEqualTo(1)
    }

    @Test
    fun `test data defaults`() {
        assertThat(TestData.datasetATotals).hasSize(14)
        assertThat(TestData.datasetH).hasSize(12)
        assertThat(TestData.defaultContainers(VolumeUnit.ML).map { it.volumeMl }).containsExactly(250, 500, 750)
        assertThat(TestData.importedEntry(now).isEditable).isFalse()
    }
}
