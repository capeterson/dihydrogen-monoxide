package casa.peterson.dhmo.sync.healthconnect

import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.database.entity.HcOp
import casa.peterson.dhmo.core.database.entity.HcPendingOpEntity
import casa.peterson.dhmo.core.model.Source
import casa.peterson.dhmo.core.model.settings.HealthConnectSettings
import casa.peterson.dhmo.core.model.sync.HcSetting
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HcPushPullTest {
    private var env = EngineTestEnvironment(HealthConnectSettings(writeHydration = true, importHydration = true))

    @After
    fun tearDown() = env.close()

    private suspend fun records(): List<HydrationRecord> = env.client
        .readRecords(
            ReadRecordsRequest(
                HydrationRecord::class,
                TimeRangeFilter.between(env.clock.instant().minus(Duration.ofDays(2)), env.clock.instant().plus(Duration.ofDays(1))),
            ),
        ).records

    private suspend fun queue(entryId: java.util.UUID, op: HcOp) =
        env.db.hcSyncDao().enqueue(HcPendingOpEntity(entryId = entryId, op = op, enqueuedAt = env.clock.instant()))

    // region push

    @Test
    fun `an upsert op writes a record with the entry id as client id and stores the record id`() = runTest {
        env.grant(HcSetting.WRITE_HYDRATION)
        val entry = env.entry(300)
        env.db.intakeDao().upsert(entry)
        queue(entry.id, HcOp.UPSERT)
        env.engine.sync()
        val record = records().single()
        assertThat(record.volume.inMilliliters).isEqualTo(300.0)
        assertThat(record.metadata.clientRecordId).isEqualTo(entry.id.toString())
        assertThat(env.db.intakeDao().getById(entry.id)!!.hcRecordId).isEqualTo(record.metadata.id)
        assertThat(env.db.hcSyncDao().allOps()).isEmpty()
    }

    @Test
    fun `an edit upserts the same record and a delete removes it`() = runTest {
        env.grant(HcSetting.WRITE_HYDRATION)
        val entry = env.entry(300)
        env.db.intakeDao().upsert(entry)
        queue(entry.id, HcOp.UPSERT)
        env.engine.sync()
        env.clock.advanceBy(Duration.ofMinutes(1))
        env.db.intakeDao().upsert(entry.copy(volumeMl = 500, updatedAt = env.clock.instant()))
        queue(entry.id, HcOp.UPSERT)
        env.engine.sync()
        assertThat(records().map { it.volume.inMilliliters }).containsExactly(500.0)
        queue(entry.id, HcOp.DELETE)
        env.engine.sync()
        assertThat(records()).isEmpty()
    }

    @Test
    fun `imports and tombstones are never pushed and their ops are dropped`() = runTest {
        env.grant(HcSetting.WRITE_HYDRATION)
        val imported = env.entry(source = Source.HEALTH_CONNECT_IMPORT)
        val deleted = env.entry()
        env.db.intakeDao().upsert(imported)
        env.db.intakeDao().upsert(deleted.copy(deletedAt = env.clock.instant()))
        queue(imported.id, HcOp.UPSERT)
        queue(deleted.id, HcOp.UPSERT)
        env.engine.sync()
        assertThat(records()).isEmpty()
        assertThat(env.db.hcSyncDao().allOps()).isEmpty()
    }

    @Test
    fun `a revoked write permission leaves the op queued for later`() = runTest {
        val entry = env.entry()
        env.db.intakeDao().upsert(entry)
        queue(entry.id, HcOp.UPSERT)
        // writeHydration is on in settings but WRITE_HYDRATION was never granted: the setting is not effective.
        val report = env.engine.sync()
        assertThat(report.missingPermissions).contains("android.permission.health.WRITE_HYDRATION")
        assertThat(records()).isEmpty()
        assertThat(env.db.hcSyncDao().allOps()).hasSize(1)
    }

    // endregion

    // region pull

    @Test
    fun `other apps' water is imported once and the source is remembered`() = runTest {
        env.grant(HcSetting.IMPORT_HYDRATION)
        env.insertFrom("com.fitbit.FitbitMobile", 330.0)
        val report = env.engine.sync()
        val rows = env.db.intakeDao().importsBetween(Instant.EPOCH, Instant.parse("2030-01-01T00:00:00Z"))
        assertThat(rows).hasSize(1)
        assertThat(rows.single().volumeMl).isEqualTo(330)
        assertThat(rows.single().hcDataOrigin).isEqualTo("com.fitbit.FitbitMobile")
        assertThat(env.settings.current().healthConnect.importSources).containsEntry("com.fitbit.FitbitMobile", true)
        assertThat(report.changedDays).isNotEmpty()
        env.engine.sync() // a second run changes nothing
        assertThat(env.db.intakeDao().importsBetween(Instant.EPOCH, Instant.parse("2030-01-01T00:00:00Z"))).hasSize(1)
    }

    @Test
    fun `own records and excluded apps are ignored`() = runTest {
        env.grant(HcSetting.IMPORT_HYDRATION)
        env.settings.update { it.copy(healthConnect = it.healthConnect.copy(importSources = mapOf("com.muted" to false))) }
        env.insertFrom("com.muted", 200.0)
        env.insertFrom(env.context.packageName, 250.0)
        env.engine.sync()
        assertThat(env.db.intakeDao().importsBetween(Instant.EPOCH, Instant.parse("2030-01-01T00:00:00Z"))).isEmpty()
    }

    @Test
    fun `nothing is imported while import is off`() = runTest {
        env = EngineTestEnvironment(HealthConnectSettings(importHydration = false, writeHydration = true))
        env.grant(HcSetting.WRITE_HYDRATION)
        env.insertFrom("com.fitbit.FitbitMobile", 330.0)
        env.engine.sync()
        assertThat(env.db.intakeDao().importsBetween(Instant.EPOCH, Instant.parse("2030-01-01T00:00:00Z"))).isEmpty()
    }

    @Test
    fun `a record deleted in Health Connect tombstones the import, an adopted one just loses its link`() = runTest {
        env.grant(HcSetting.IMPORT_HYDRATION)
        val keep = env.insertFrom("com.app", 100.0, env.clock.instant().minusSeconds(60))
        val adopt = env.insertFrom("com.app", 200.0, env.clock.instant().minusSeconds(120))
        env.engine.sync()
        val adoptedRow = env.db.intakeDao().getByHcRecordId(adopt)!!
        env.db.intakeDao().upsert(adoptedRow.copy(hcAdopted = true))
        env.client.setPackageName("com.app")
        env.client.deleteRecords(HydrationRecord::class, listOf(keep, adopt), emptyList())
        env.engine.sync()
        assertThat(env.db.intakeDao().getByHcRecordId(keep)!!.deletedAt).isNotNull()
        assertThat(env.db.intakeDao().getById(adoptedRow.id)!!.deletedAt).isNull()
        assertThat(env.db.intakeDao().getById(adoptedRow.id)!!.hcRecordId).isNull()
    }

    @Test
    fun `an adopted row ignores later updates from the source app`() = runTest {
        env.grant(HcSetting.IMPORT_HYDRATION)
        val id = env.insertFrom("com.app", 100.0, clientRecordId = "c1", version = 1)
        env.engine.sync()
        val row = env.db.intakeDao().getByHcRecordId(id)!!
        env.db.intakeDao().upsert(row.copy(hcAdopted = true, volumeMl = 123))
        env.insertFrom("com.app", 900.0, clientRecordId = "c1", version = 2) // the source app edits it
        env.engine.sync()
        assertThat(env.db.intakeDao().getById(row.id)!!.volumeMl).isEqualTo(123)
    }

    @Test
    fun `an update from the source app changes a row that was not adopted`() = runTest {
        env.grant(HcSetting.IMPORT_HYDRATION)
        val id = env.insertFrom("com.app", 100.0, clientRecordId = "c1", version = 1)
        env.engine.sync()
        env.insertFrom("com.app", 900.0, clientRecordId = "c1", version = 2)
        env.engine.sync()
        assertThat(env.db.intakeDao().getByHcRecordId(id)!!.volumeMl).isEqualTo(900)
    }

    @Test
    fun `same origin, instant and volume with a new record id adopts the id and inserts nothing`() = runTest {
        env.grant(HcSetting.IMPORT_HYDRATION)
        val at = env.clock.instant().minusSeconds(30)
        val first = env.insertFrom("com.app", 250.0, at)
        env.engine.sync()
        val row = env.db.intakeDao().getByHcRecordId(first)!!
        // After a restore onto a new phone the same drink comes back under a new record id.
        env.client.setPackageName("com.app")
        env.client.deleteRecords(HydrationRecord::class, listOf(first), emptyList())
        val second = env.insertFrom("com.app", 250.0, at)
        env.db.hcSyncDao().deleteState("hc.changes_token") // force the reconcile path
        env.engine.sync()
        assertThat(env.db.intakeDao().importsBetween(Instant.EPOCH, Instant.parse("2030-01-01T00:00:00Z")).filter { it.deletedAt == null })
            .hasSize(1)
        assertThat(env.db.intakeDao().getById(row.id)!!.hcRecordId).isEqualTo(second)
    }

    @Test
    fun `an expired token reconciles, tombstones what vanished and stores a new token`() = runTest {
        env.grant(HcSetting.IMPORT_HYDRATION)
        val gone = env.insertFrom("com.app", 100.0, env.clock.instant().minusSeconds(60))
        env.insertFrom("com.app", 150.0, env.clock.instant().minusSeconds(30))
        env.engine.sync()
        val token = env.db.hcSyncDao().getState("hc.changes_token")!!
        env.client.setPackageName("com.app")
        env.client.deleteRecords(HydrationRecord::class, listOf(gone), emptyList())
        env.client.expireToken(token)
        env.engine.sync()
        assertThat(env.db.intakeDao().getByHcRecordId(gone)!!.deletedAt).isNotNull()
        assertThat(env.db.hcSyncDao().getState("hc.changes_token")).isNotEqualTo(token)
    }

    @Test
    fun `a revoked permission is reported and stops that part of the sync`() = runTest {
        // IMPORT_HYDRATION is on in settings but never granted.
        env.insertFrom("com.app", 100.0)
        val report = env.engine.sync()
        assertThat(report.missingPermissions).contains("android.permission.health.READ_HYDRATION")
        assertThat(env.db.intakeDao().importsBetween(Instant.EPOCH, Instant.parse("2030-01-01T00:00:00Z"))).isEmpty()
    }

    // endregion
}
