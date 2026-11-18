package casa.peterson.dhmo.sync.healthconnect

import android.content.Context
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.testing.FakeHealthConnectClient
import androidx.health.connect.client.testing.FakePermissionController
import androidx.health.connect.client.units.Volume
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import casa.peterson.dhmo.core.database.DhmoDatabase
import casa.peterson.dhmo.core.database.entity.IntakeEntryEntity
import casa.peterson.dhmo.core.domain.goal.GoalPolicyV1
import casa.peterson.dhmo.core.model.Source
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.settings.HealthConnectSettings
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.FixedZoneProvider
import casa.peterson.dhmo.core.testing.MutableClock
import casa.peterson.dhmo.sync.healthconnect.engine.HcPermissions
import casa.peterson.dhmo.sync.healthconnect.engine.HealthConnectClientProvider
import casa.peterson.dhmo.sync.healthconnect.engine.HealthConnectSyncEngine
import casa.peterson.dhmo.core.model.sync.HcSetting
import io.mockk.mockk
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** A real engine over an in-memory Room database and the Health Connect fake. */
internal class EngineTestEnvironment(hc: HealthConnectSettings = HealthConnectSettings()) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val db: DhmoDatabase = Room.inMemoryDatabaseBuilder(context, DhmoDatabase::class.java).allowMainThreadQueries().build()
    val clock = MutableClock(Instant.parse("2026-11-17T12:00:00Z"))
    val permissions = FakePermissionController(grantAll = false)
    val client = FakeHealthConnectClient(context.packageName, clock, permissions)
    val settings = FakeSettingsRepository(AppSettings(healthConnect = hc))
    val engine = HealthConnectSyncEngine(
        HealthConnectClientProvider { client },
        db,
        settings,
        GoalPolicyV1,
        mockk(relaxed = true),
        { emptySet() },
        FixedZoneProvider(java.time.ZoneId.of("UTC")),
        clock,
        context,
    )

    fun grant(vararg settings: HcSetting) {
        permissions.grantPermissions(settings.flatMap { HcPermissions.forSetting(it) }.toSet())
    }

    fun entry(volumeMl: Int = 250, at: Instant = clock.instant(), source: Source = Source.MANUAL, id: UUID = UUID.randomUUID()) = IntakeEntryEntity(
        id = id,
        instant = at,
        zoneOffsetSeconds = 0,
        hydrationDay = LocalDate.ofInstant(at, ZoneOffset.UTC),
        volumeMl = volumeMl,
        source = source,
        createdAt = at,
        updatedAt = at,
    )

    /** Pretends another app wrote this record to Health Connect. */
    suspend fun insertFrom(
        packageName: String,
        ml: Double,
        at: Instant = clock.instant(),
        clientRecordId: String? = null,
        version: Long = 0,
    ): String {
        val own = context.packageName
        client.setPackageName(packageName)
        val id = client.insertRecords(
            listOf(
                HydrationRecord(
                    startTime = at,
                    startZoneOffset = ZoneOffset.UTC,
                    endTime = at.plusSeconds(1),
                    endZoneOffset = ZoneOffset.UTC,
                    volume = Volume.milliliters(ml),
                    metadata = if (clientRecordId == null) Metadata.manualEntry() else Metadata.manualEntry(clientRecordId, version),
                ),
            ),
        ).recordIdsList.single()
        client.setPackageName(own)
        return id
    }


    fun close() = db.close()
}
