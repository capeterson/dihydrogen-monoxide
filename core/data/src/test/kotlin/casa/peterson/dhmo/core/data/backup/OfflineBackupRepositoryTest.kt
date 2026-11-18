package casa.peterson.dhmo.core.data.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.backup.BackupCodec
import casa.peterson.dhmo.core.backup.BackupDocument
import casa.peterson.dhmo.core.data.RoomTestEnvironment
import casa.peterson.dhmo.core.data.repository.EntryChangeListener
import casa.peterson.dhmo.core.data.repository.OfflineIntakeRepository
import casa.peterson.dhmo.core.database.DhmoDatabase
import casa.peterson.dhmo.core.database.entity.ActivityAdjustmentEntity
import casa.peterson.dhmo.core.database.entity.ContainerEntity
import casa.peterson.dhmo.core.database.entity.DailyGoalEntity
import casa.peterson.dhmo.core.database.entity.DayOverrideEntity
import casa.peterson.dhmo.core.database.entity.HcOp
import casa.peterson.dhmo.core.database.entity.HcPendingOpEntity
import casa.peterson.dhmo.core.database.entity.HealthDailyEntity
import casa.peterson.dhmo.core.database.entity.IntakeEntryEntity
import casa.peterson.dhmo.core.database.entity.ProfileSnapshotEntity
import casa.peterson.dhmo.core.database.entity.SyncStateEntity
import casa.peterson.dhmo.core.database.entity.WeatherDayEntity
import casa.peterson.dhmo.core.domain.goal.GoalPolicyV1
import casa.peterson.dhmo.core.model.ActivityLevel
import casa.peterson.dhmo.core.model.ActivitySource
import casa.peterson.dhmo.core.model.ChangeOrigin
import casa.peterson.dhmo.core.model.Climate
import casa.peterson.dhmo.core.model.ContainerIcon
import casa.peterson.dhmo.core.model.EntryChange
import casa.peterson.dhmo.core.model.Gender
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.model.Intensity
import casa.peterson.dhmo.core.model.PregnancyStatus
import casa.peterson.dhmo.core.model.ProfileSource
import casa.peterson.dhmo.core.model.Source
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.settings.BackupSettings
import casa.peterson.dhmo.core.model.settings.HealthConnectSettings
import casa.peterson.dhmo.core.model.settings.ReminderSettings
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.core.model.sync.RestoreMode
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.FixedZoneProvider
import casa.peterson.dhmo.core.testing.MutableClock
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineBackupRepositoryTest {
    @get:Rule val source = RoomTestEnvironment()

    @get:Rule val target = RoomTestEnvironment()

    @get:Rule val tmp = TemporaryFolder()

    private val now = Instant.parse("2026-11-15T09:30:00Z")
    private val clock = MutableClock(now)
    private val changes = mutableListOf<EntryChange>()
    private var afterRestoreCalls = 0

    private fun repository(
        env: RoomTestEnvironment,
        settings: FakeSettingsRepository,
        dir: File = tmp.newFolder(),
    ): OfflineBackupRepository {
        val db = env.db
        val listener = object : EntryChangeListener {
            override suspend fun onEntriesChanged(change: EntryChange) {
                changes += change
            }
        }
        val intake = OfflineIntakeRepository(
            db.intakeDao(),
            db.dailyTotalDao(),
            db.hcSyncDao(),
            env.transactions,
            settings,
            clock,
            FixedZoneProvider(),
            setOf(listener),
        )
        val environment = object : BackupEnvironment {
            override val appVersionName = "1.2.3"
            override val appVersionCode = 7
            override val deviceName = "Test phone"
            override val safetyDir = dir
        }
        return OfflineBackupRepository(
            db.intakeDao(), db.containerDao(), db.profileDao(), db.goalDao(), db.activityDao(), db.weatherDao(),
            db.healthDailyDao(), db.hcSyncDao(), env.transactions, settings, intake, GoalPolicyV1, environment,
            { afterRestoreCalls++ }, clock,
        )
    }

    private val day = LocalDate.of(2026, 11, 14)
    private val t0 = Instant.parse("2026-11-14T08:00:00.123Z")

    private fun entry(id: Int, updatedAt: Instant = t0, deletedAt: Instant? = null, volumeMl: Int = 250, day: LocalDate = this.day) =
        IntakeEntryEntity(
            id = uuid(id), instant = t0.plusSeconds(id * 60L), zoneOffsetSeconds = 3600, hydrationDay = day, volumeMl = volumeMl,
            source = Source.MANUAL, createdAt = t0, updatedAt = updatedAt, deletedAt = deletedAt,
        )

    private fun breakdownJson(age: Int, ml: Int) =
        """{"policyVersion":1,"ageYears":$age,"components":[],"rawMl":$ml,"clampedMl":$ml,"calculatedMl":$ml,"clamp":"NONE"}"""

    private fun uuid(n: Int) = UUID.fromString("00000000-0000-4000-8000-%012d".format(n))

    /** Every table, with optional values both set and unset, tombstones, and device-local rows that must not be exported. */
    @Suppress("LongMethod") // one row set per table, kept together so the round trip covers them all
    private suspend fun seedEverything(db: DhmoDatabase) {
        db.intakeDao().upsertAll(
            listOf(
                entry(1).copy(note = "after run", hcRecordId = "hc-1"),
                entry(2, deletedAt = t0.plusSeconds(5)),
                entry(3).copy(source = Source.HEALTH_CONNECT_IMPORT, hcDataOrigin = "com.example.fit", hcAdopted = true),
            ),
        )
        db.containerDao().upsertAll(
            listOf(
                ContainerEntity(uuid(10), "Glass", 250, ContainerIcon.GLASS, 0, t0),
                ContainerEntity(uuid(11), "Old flask", 750, ContainerIcon.FLASK, 1, t0, deletedAt = t0),
            ),
        )
        db.profileDao().insertAll(
            listOf(
                ProfileSnapshotEntity(
                    uuid(20), day.minusDays(30), LocalDate.of(1990, 5, 1), Gender.FEMALE, 61.5, 168.0, ActivityLevel.MODERATE,
                    Climate.TEMPERATE, PregnancyStatus.NONE, lactating = false, highAltitude = false, ProfileSource.MANUAL, t0,
                ),
                ProfileSnapshotEntity(
                    uuid(21), day, LocalDate.of(1990, 5, 1), Gender.FEMALE, 60.0, null, ActivityLevel.VERY_ACTIVE,
                    Climate.HOT_HUMID, PregnancyStatus.PREGNANT, lactating = true, highAltitude = true, ProfileSource.HEALTH_CONNECT, t0,
                ),
            ),
        )
        db.goalDao().upsertGoals(
            listOf(
                DailyGoalEntity(
                    day,
                    2300,
                    2300,
                    GoalMode.CALCULATED,
                    breakdownJson(age = 36, ml = 2300),
                    240,
                    1,
                    t0,
                ),
                DailyGoalEntity(
                    day.minusDays(1),
                    3000,
                    null,
                    GoalMode.GLOBAL_OVERRIDE,
                    breakdownJson(age = 0, ml = 0),
                    0,
                    1,
                    t0,
                ),
            ),
        )
        db.goalDao().upsertOverrides(
            listOf(DayOverrideEntity(day, 3000, "hike", t0), DayOverrideEntity(day.minusDays(2), 2000, null, t0, deletedAt = t0)),
        )
        db.activityDao().upsertAll(
            listOf(
                ActivityAdjustmentEntity(ActivitySource.HEALTH_CONNECT, "ex-1", day, 45, Intensity.VIGOROUS, 500, 56, t0),
                ActivityAdjustmentEntity(ActivitySource.MANUAL_PLANNED, "plan-1", day, 30, Intensity.LIGHT, 100, null, t0),
            ),
        )
        db.weatherDao().upsertAll(
            listOf(
                WeatherDayEntity(day, 525, 134, 31.5, 33.0, 40.0, 250, t0),
                WeatherDayEntity(day.minusDays(1), 525, 134, 20.0, 19.5, null, 0, t0),
            ),
        )
        db.healthDailyDao().upsertAll(listOf(HealthDailyEntity(day, 9000, 420.5, t0), HealthDailyEntity(day.minusDays(1), null, null, t0)))
        db.hcSyncDao().insertOp(HcPendingOpEntity(entryId = uuid(1), op = HcOp.UPSERT, enqueuedAt = t0))
        db.hcSyncDao().putState(SyncStateEntity("changes_token", "secret-token", t0))
    }

    private suspend fun dump(db: DhmoDatabase): List<Any> = listOf(
        db.intakeDao().getAllIncludingDeleted().sortedBy { it.id }.map { it.copy(hcRecordId = null) },
        db.containerDao().getAllIncludingDeleted().sortedBy { it.id },
        db.profileDao().getAll(),
        db.goalDao().getAllGoals().sortedBy { it.date },
        db.goalDao().getAllOverridesIncludingDeleted().sortedBy { it.date },
        db.activityDao().getAll().sortedBy { it.sourceRecordId },
        db.weatherDao().getAll().sortedBy { it.date },
        db.healthDailyDao().getAll().sortedBy { it.date },
    )

    @Test
    fun `export then replace-restore into an empty database reproduces every row`() = runTest {
        seedEverything(source.db)
        val sourceSettings = FakeSettingsRepository(AppSettings(units = VolumeUnit.US_FL_OZ, dayStartMinutes = 240))
        val exported = repository(source, sourceSettings).exportDocument()
        // Through the real container, as a JSON export file would be.
        val decoded = BackupCodec.decode(BackupCodec.encodeJson(exported, pretty = true))
        assertThat(decoded).isEqualTo(exported)

        val targetSettings = FakeSettingsRepository()
        repository(target, targetSettings).restore(decoded, RestoreMode.REPLACE)

        assertThat(dump(target.db)).isEqualTo(dump(source.db))
        assertThat(targetSettings.current().units).isEqualTo(VolumeUnit.US_FL_OZ)
        assertThat(targetSettings.current().dayStartMinutes).isEqualTo(240)
        assertThat(changes.single().origin).isEqualTo(ChangeOrigin.RESTORE)
        assertThat(changes.single().affectedDays).containsExactly(day)
        assertThat(afterRestoreCalls).isEqualTo(1)
    }

    @Test
    fun `export carries metadata and counts and never device-local sync state`() = runTest {
        seedEverything(source.db)
        val doc = repository(source, FakeSettingsRepository()).exportDocument()

        assertThat(doc.appVersion).isEqualTo("1.2.3")
        assertThat(doc.appVersionCode).isEqualTo(7)
        assertThat(doc.deviceName).isEqualTo("Test phone")
        assertThat(doc.dbVersion).isEqualTo(DhmoDatabase.VERSION)
        assertThat(doc.policyVersion).isEqualTo(GoalPolicyV1.version)
        assertThat(doc.createdAt).isEqualTo(now)
        assertThat(doc.counts.intakeEntries).isEqualTo(2)
        assertThat(doc.counts.intakeTombstones).isEqualTo(1)
        assertThat(doc.counts.containers).isEqualTo(2)
        assertThat(doc.counts.healthDaily).isEqualTo(2)
        val json = String(BackupCodec.encodeJson(doc), Charsets.UTF_8)
        assertThat(json).doesNotContain("secret-token")
        assertThat(json).doesNotContain("hc-1")
        assertThat(json).doesNotContain("changes_token")
        assertThat(json).doesNotContain("enqueuedAt")
    }

    @Test
    fun `replace clears the Health Connect queue and sync state`() = runTest {
        seedEverything(target.db)
        val doc = repository(source, FakeSettingsRepository()).exportDocument() // empty source

        repository(target, FakeSettingsRepository()).restore(doc, RestoreMode.REPLACE)

        assertThat(target.db.hcSyncDao().allOps()).isEmpty()
        assertThat(target.db.hcSyncDao().allStates()).isEmpty()
        assertThat(target.db.intakeDao().getAllIncludingDeleted()).isEmpty()
    }

    @Test
    fun `replace sanitises settings`() = runTest {
        val incoming = AppSettings(
            units = VolumeUnit.L,
            reminders = ReminderSettings(enabled = true),
            backup = BackupSettings(driveEnabled = false, keepDaily = 3),
            healthConnect = HealthConnectSettings(
                writeHydration = true,
                importHydration = true,
                importSources = mapOf("com.example.fit" to false),
                syncWeight = true,
                useExercise = true,
                readStepsAndCalories = true,
                backgroundSync = true,
                importHistory = true,
            ),
        )
        val doc = repository(source, FakeSettingsRepository(incoming)).exportDocument()
        val local = AppSettings(backup = BackupSettings(driveEnabled = true, encryptionEnabled = true))
        val settings = FakeSettingsRepository(local)

        repository(target, settings).restore(doc, RestoreMode.REPLACE)

        val result = settings.current()
        assertThat(result.units).isEqualTo(VolumeUnit.L)
        assertThat(result.reminders.enabled).isTrue()
        assertThat(result.backup).isEqualTo(local.backup)
        assertThat(result.healthConnect).isEqualTo(HealthConnectSettings(importSources = mapOf("com.example.fit" to false)))
    }

    @Test
    fun `merge applies the newer row and keeps local settings and hc ids`() = runTest {
        val later = t0.plus(Duration.ofHours(1))
        // Local: 1 (edited later), 2 (live), 4 (local only).
        target.db.intakeDao().upsertAll(
            listOf(
                entry(1, updatedAt = later, volumeMl = 500).copy(hcRecordId = "hc-1"),
                entry(2).copy(hcRecordId = "hc-2"),
                entry(4),
            ),
        )
        target.db.goalDao().upsertOverrides(listOf(DayOverrideEntity(day, 2500, "local", later)))
        // Incoming: 1 (older edit), 2 (deleted later), 3 (new, another day), and an older override.
        source.db.intakeDao().upsertAll(
            listOf(
                entry(1, volumeMl = 300),
                entry(2, updatedAt = later, deletedAt = later),
                entry(3, day = day.minusDays(1)),
            ),
        )
        source.db.goalDao().upsertOverrides(listOf(DayOverrideEntity(day, 4000, "incoming", t0)))
        val incoming = repository(source, FakeSettingsRepository(AppSettings(units = VolumeUnit.L))).exportDocument()
        val localSettings = AppSettings(units = VolumeUnit.US_FL_OZ, healthConnect = HealthConnectSettings(writeHydration = true))
        val settings = FakeSettingsRepository(localSettings)

        repository(target, settings).restore(incoming, RestoreMode.MERGE)

        val rows = target.db.intakeDao().getAllIncludingDeleted().associateBy { it.id }
        assertThat(rows.keys).containsExactly(uuid(1), uuid(2), uuid(3), uuid(4))
        assertThat(rows.getValue(uuid(1)).volumeMl).isEqualTo(500) // local edit is newer
        assertThat(rows.getValue(uuid(1)).hcRecordId).isEqualTo("hc-1")
        assertThat(rows.getValue(uuid(2)).deletedAt).isEqualTo(later) // newer deletion wins
        assertThat(rows.getValue(uuid(2)).hcRecordId).isEqualTo("hc-2")
        assertThat(target.db.goalDao().getOverride(day)?.reason).isEqualTo("local")
        assertThat(settings.current()).isEqualTo(localSettings)
        // Writing to Health Connect is on: the two rows that changed are queued.
        val ops = target.db.hcSyncDao().allOps().associate { it.entryId to it.op }
        assertThat(ops).containsExactly(uuid(2), HcOp.DELETE, uuid(3), HcOp.UPSERT)
        assertThat(changes.single().affectedDays).containsExactly(day, day.minusDays(1))
    }

    @Test
    fun `restore writes a safety snapshot and undo brings the previous data back`() = runTest {
        seedEverything(target.db)
        val before = dump(target.db)
        val settings = FakeSettingsRepository(AppSettings(healthConnect = HealthConnectSettings(writeHydration = true)))
        val repo = repository(target, settings)
        val empty = repository(source, FakeSettingsRepository()).exportDocument()

        repo.restore(empty, RestoreMode.REPLACE)
        assertThat(target.db.intakeDao().getAllIncludingDeleted()).isEmpty()
        val snapshot = repo.latestSafetySnapshot()
        assertThat(snapshot?.name).isEqualTo("pre-restore-20261115T093000Z.json.gz")

        assertThat(repo.undoLastRestore()).isTrue()
        assertThat(dump(target.db)).isEqualTo(before)
        // Undo restores this device's own settings without sanitising them.
        assertThat(settings.current().healthConnect.writeHydration).isTrue()
        assertThat(repo.latestSafetySnapshot()).isNull()
        assertThat(repo.undoLastRestore()).isFalse()
    }

    @Test
    fun `only the newest three safety snapshots are kept`() = runTest {
        val dir = tmp.newFolder()
        val repo = repository(target, FakeSettingsRepository(), dir)
        repeat(4) {
            repo.writeSafetySnapshot()
            clock.advanceBy(Duration.ofSeconds(1))
        }
        repo.writeSafetySnapshot()
        val sameSecond = repo.writeSafetySnapshot()

        assertThat(dir.list()!!.sorted()).containsExactly(
            "pre-restore-20261115T093003Z.json.gz",
            "pre-restore-20261115T093004Z.json.gz",
            "pre-restore-20261115T093004Z-1.json.gz",
        )
        assertThat(repo.latestSafetySnapshot()).isEqualTo(sameSecond)
        assertThat(BackupCodec.decode(sameSecond.readBytes())).isInstanceOf(BackupDocument::class.java)
    }
}
