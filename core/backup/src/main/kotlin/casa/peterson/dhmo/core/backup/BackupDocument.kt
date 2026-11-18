@file:UseSerializers(InstantIsoSerializer::class, LocalDateIsoSerializer::class)

package casa.peterson.dhmo.core.backup

import casa.peterson.dhmo.core.model.GoalBreakdown
import casa.peterson.dhmo.core.model.serialization.InstantIsoSerializer
import casa.peterson.dhmo.core.model.serialization.LocalDateIsoSerializer
import casa.peterson.dhmo.core.model.settings.AppSettings
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonNames

/**
 * The JSON document of TECHNICAL_PLAN §11.2: the JSON export and the Drive backup.
 * T5 adds the codec, crypto, merge, retention and CSV around it.
 */
@Serializable
data class BackupDocument(
    val format: String = "dhmo-backup",
    val schemaVersion: Int = 1,
    val appVersion: String,
    val appVersionCode: Int,
    /** Room version that produced it. */
    val dbVersion: Int,
    /** GoalPolicy version in use. */
    val policyVersion: Int,
    val createdAt: Instant,
    /** `Settings.Global.DEVICE_NAME`, falling back to `Build.MODEL`. */
    val deviceName: String,
    val counts: BackupCounts,
    val entities: BackupEntities,
    /** The whole user-settings object; DeviceState is never included. */
    val settings: AppSettings,
)

@Serializable
data class BackupCounts(
    /** Live entries (`deletedAt == null`): the number shown in the restore list. */
    val intakeEntries: Int,
    val intakeTombstones: Int,
    val containers: Int,
    val profileSnapshots: Int,
    val dailyGoals: Int,
    val dayOverrides: Int,
    val activityAdjustments: Int,
    val weatherDays: Int,
    val healthDaily: Int,
)

/** Row counts of [entities], as stored in [BackupDocument.counts]. */
fun toCounts(entities: BackupEntities): BackupCounts = BackupCounts(
    intakeEntries = entities.intakeEntries.count { it.deletedAt == null },
    intakeTombstones = entities.intakeEntries.count { it.deletedAt != null },
    containers = entities.containers.size,
    profileSnapshots = entities.profileSnapshots.size,
    dailyGoals = entities.dailyGoals.size,
    dayOverrides = entities.dayOverrides.size,
    activityAdjustments = entities.activityAdjustments.size,
    weatherDays = entities.weatherDays.size,
    healthDaily = entities.healthDaily.size,
)

@Serializable
data class BackupEntities(
    val intakeEntries: List<IntakeEntryDto> = emptyList(),
    val containers: List<ContainerDto> = emptyList(),
    val profileSnapshots: List<ProfileSnapshotDto> = emptyList(),
    val dailyGoals: List<DailyGoalDto> = emptyList(),
    val dayOverrides: List<DayOverrideDto> = emptyList(),
    val activityAdjustments: List<ActivityAdjustmentDto> = emptyList(),
    val weatherDays: List<WeatherDayDto> = emptyList(),
    val healthDaily: List<HealthDailyDto> = emptyList(),
)

/** `hcRecordId` is device-local and is not exported. */
@Serializable
data class IntakeEntryDto(
    val id: String,
    val instant: Instant,
    val zoneOffsetSeconds: Int,
    val hydrationDay: LocalDate,
    val volumeMl: Int,
    val source: String,
    val hcDataOrigin: String? = null,
    val hcAdopted: Boolean = false,
    val note: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
)

@Serializable
data class ContainerDto(
    val id: String,
    val name: String,
    val volumeMl: Int,
    val icon: String,
    val sortOrder: Int,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ProfileSnapshotDto(
    val id: String,
    val effectiveFrom: LocalDate,
    val birthDate: LocalDate,
    /** Backups made before the rename call this field `sex`; both are read. */
    @JsonNames("sex") val gender: String,
    val weightKg: Double,
    val heightCm: Double? = null,
    val activityLevel: String,
    val climate: String,
    val pregnancyStatus: String,
    val lactating: Boolean,
    val highAltitude: Boolean,
    val source: String,
    val createdAt: Instant,
)

@Serializable
data class DailyGoalDto(
    val date: LocalDate,
    val targetMl: Int,
    val calculatedMl: Int? = null,
    val mode: String,
    /** Embedded as an object, not as a string. */
    val breakdown: GoalBreakdown? = null,
    val dayStartMinutes: Int,
    val policyVersion: Int,
    val updatedAt: Instant,
)

@Serializable
data class DayOverrideDto(
    val date: LocalDate,
    val targetMl: Int,
    val reason: String? = null,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
)

@Serializable
data class ActivityAdjustmentDto(
    val source: String,
    val sourceRecordId: String,
    val date: LocalDate,
    val minutes: Int,
    val intensity: String,
    val extraMl: Int,
    val exerciseType: Int? = null,
    val updatedAt: Instant,
)

@Serializable
data class WeatherDayDto(
    val date: LocalDate,
    val latE1: Int,
    val lonE1: Int,
    val maxTempC: Double,
    val maxApparentTempC: Double,
    val meanRelHumidity: Double? = null,
    val extraMl: Int,
    val fetchedAt: Instant,
)

@Serializable
data class HealthDailyDto(val date: LocalDate, val steps: Long? = null, val activeKcal: Double? = null, val updatedAt: Instant)
