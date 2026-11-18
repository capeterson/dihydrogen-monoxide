package casa.peterson.dhmo.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import casa.peterson.dhmo.core.model.ActivityLevel
import casa.peterson.dhmo.core.model.ActivitySource
import casa.peterson.dhmo.core.model.Climate
import casa.peterson.dhmo.core.model.ContainerIcon
import casa.peterson.dhmo.core.model.Gender
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.model.Intensity
import casa.peterson.dhmo.core.model.PregnancyStatus
import casa.peterson.dhmo.core.model.ProfileSource
import casa.peterson.dhmo.core.model.Source
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity(
    tableName = "intake_entry",
    indices = [
        Index(value = ["hydration_day"], name = "index_intake_entry_hydration_day"),
        Index(value = ["instant"], name = "index_intake_entry_instant"),
        Index(value = ["hc_record_id"], name = "index_intake_entry_hc_record_id", unique = true),
    ],
)
data class IntakeEntryEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: UUID,
    @ColumnInfo(name = "instant") val instant: Instant,
    @ColumnInfo(name = "zone_offset_seconds") val zoneOffsetSeconds: Int,
    @ColumnInfo(name = "hydration_day") val hydrationDay: LocalDate,
    @ColumnInfo(name = "volume_ml") val volumeMl: Int,
    @ColumnInfo(name = "source") val source: Source,
    @ColumnInfo(name = "hc_record_id") val hcRecordId: String? = null,
    @ColumnInfo(name = "hc_data_origin") val hcDataOrigin: String? = null,
    @ColumnInfo(name = "hc_adopted", defaultValue = "0") val hcAdopted: Boolean = false,
    @ColumnInfo(name = "note") val note: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null,
)

@Entity(tableName = "container", indices = [Index(value = ["sort_order"], name = "index_container_sort_order")])
data class ContainerEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: UUID,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "volume_ml") val volumeMl: Int,
    @ColumnInfo(name = "icon") val icon: ContainerIcon,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null,
)

@Entity(
    tableName = "profile_snapshot",
    indices = [Index(value = ["effective_from", "created_at"], name = "index_profile_snapshot_effective_from_created_at")],
)
data class ProfileSnapshotEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: UUID,
    @ColumnInfo(name = "effective_from") val effectiveFrom: LocalDate,
    @ColumnInfo(name = "birth_date") val birthDate: LocalDate,
    @ColumnInfo(name = "gender") val gender: Gender,
    @ColumnInfo(name = "weight_kg") val weightKg: Double,
    @ColumnInfo(name = "height_cm") val heightCm: Double? = null,
    @ColumnInfo(name = "activity_level") val activityLevel: ActivityLevel,
    @ColumnInfo(name = "climate") val climate: Climate,
    @ColumnInfo(name = "pregnancy_status") val pregnancyStatus: PregnancyStatus,
    @ColumnInfo(name = "lactating") val lactating: Boolean,
    @ColumnInfo(name = "high_altitude") val highAltitude: Boolean,
    @ColumnInfo(name = "source") val source: ProfileSource,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

@Entity(tableName = "daily_goal")
data class DailyGoalEntity(
    @PrimaryKey @ColumnInfo(name = "date") val date: LocalDate,
    @ColumnInfo(name = "target_ml") val targetMl: Int,
    @ColumnInfo(name = "calculated_ml") val calculatedMl: Int? = null,
    @ColumnInfo(name = "mode") val mode: GoalMode,
    /** Serialized `GoalBreakdown`; always a full object. */
    @ColumnInfo(name = "breakdown_json") val breakdownJson: String,
    @ColumnInfo(name = "day_start_minutes") val dayStartMinutes: Int,
    @ColumnInfo(name = "policy_version") val policyVersion: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(tableName = "day_override")
data class DayOverrideEntity(
    @PrimaryKey @ColumnInfo(name = "date") val date: LocalDate,
    @ColumnInfo(name = "target_ml") val targetMl: Int,
    @ColumnInfo(name = "reason") val reason: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant? = null,
)

@Entity(
    tableName = "activity_adjustment",
    primaryKeys = ["source", "source_record_id"],
    indices = [Index(value = ["date"], name = "index_activity_adjustment_date")],
)
data class ActivityAdjustmentEntity(
    @ColumnInfo(name = "source") val source: ActivitySource,
    @ColumnInfo(name = "source_record_id") val sourceRecordId: String,
    @ColumnInfo(name = "date") val date: LocalDate,
    @ColumnInfo(name = "minutes") val minutes: Int,
    @ColumnInfo(name = "intensity") val intensity: Intensity,
    @ColumnInfo(name = "extra_ml") val extraMl: Int,
    @ColumnInfo(name = "exercise_type") val exerciseType: Int? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(tableName = "weather_day")
data class WeatherDayEntity(
    @PrimaryKey @ColumnInfo(name = "date") val date: LocalDate,
    @ColumnInfo(name = "lat_e1") val latE1: Int,
    @ColumnInfo(name = "lon_e1") val lonE1: Int,
    @ColumnInfo(name = "max_temp_c") val maxTempC: Double,
    @ColumnInfo(name = "max_apparent_temp_c") val maxApparentTempC: Double,
    @ColumnInfo(name = "mean_rel_humidity") val meanRelHumidity: Double? = null,
    @ColumnInfo(name = "extra_ml") val extraMl: Int,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Instant,
)

@Entity(tableName = "health_daily")
data class HealthDailyEntity(
    @PrimaryKey @ColumnInfo(name = "date") val date: LocalDate,
    @ColumnInfo(name = "steps") val steps: Long? = null,
    @ColumnInfo(name = "active_kcal") val activeKcal: Double? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

/** The Health Connect write queue (FIFO by id). */
@Entity(tableName = "hc_pending_op", indices = [Index(value = ["entry_id"], name = "index_hc_pending_op_entry_id")])
data class HcPendingOpEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "entry_id") val entryId: UUID,
    /** `UPSERT` or `DELETE`. */
    @ColumnInfo(name = "op") val op: HcOp,
    @ColumnInfo(name = "enqueued_at") val enqueuedAt: Instant,
    @ColumnInfo(name = "attempts", defaultValue = "0") val attempts: Int = 0,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
)

enum class HcOp { UPSERT, DELETE }

/** Device-local key/value state that must change in the same transaction as imported rows. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "value") val value: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@DatabaseView(
    viewName = "daily_total",
    value = """
        SELECT hydration_day AS date,
               SUM(volume_ml) AS total_ml,
               COUNT(*)       AS entry_count,
               MIN(instant)   AS first_at,
               MAX(instant)   AS last_at
        FROM intake_entry
        WHERE deleted_at IS NULL
        GROUP BY hydration_day
    """,
)
data class DailyTotalView(
    @ColumnInfo(name = "date") val date: LocalDate,
    @ColumnInfo(name = "total_ml") val totalMl: Int,
    @ColumnInfo(name = "entry_count") val entryCount: Int,
    @ColumnInfo(name = "first_at") val firstAt: Instant,
    @ColumnInfo(name = "last_at") val lastAt: Instant,
)

/** Result row of `ActivityDao.minutesPerDay`. */
data class DateMinutes(val date: LocalDate, val minutes: Int)
