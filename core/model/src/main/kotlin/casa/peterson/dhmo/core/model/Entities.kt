package casa.peterson.dhmo.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

data class IntakeEntry(
    val id: UUID,
    val instant: Instant,
    /** The offset in force when the entry was logged. */
    val zoneOffset: ZoneOffset,
    /** Stored bucket, computed at write time (§4.2). */
    val hydrationDay: LocalDate,
    val volumeMl: Int,
    val source: Source,
    val hcRecordId: String? = null,
    val hcDataOrigin: String? = null,
    val hcAdopted: Boolean = false,
    val note: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
) {
    val isImport: Boolean get() = source == Source.HEALTH_CONNECT_IMPORT

    /** Imports are read-only until the user adopts them (§10.6). */
    val isEditable: Boolean get() = !isImport || hcAdopted
}

data class Container(
    val id: UUID,
    val name: String,
    val volumeMl: Int,
    val icon: ContainerIcon,
    /** 0 is the default container (widget, tile); 0 and 1 are the notification actions. */
    val sortOrder: Int,
    val updatedAt: Instant,
)

data class DailyTotal(val date: LocalDate, val totalMl: Int, val entryCount: Int, val firstAt: Instant, val lastAt: Instant)

data class ProfileSnapshot(
    val id: UUID,
    val effectiveFrom: LocalDate,
    val profile: Profile,
    val source: ProfileSource,
    val createdAt: Instant,
)

data class DailyGoal(
    val date: LocalDate,
    val targetMl: Int,
    /** Null when no profile exists (manual-only users). */
    val calculatedMl: Int?,
    val mode: GoalMode,
    val breakdown: GoalBreakdown,
    /** The day-start boundary this hydration day used. */
    val dayStartMinutes: Int,
    val policyVersion: Int,
    val updatedAt: Instant,
)

data class DayOverride(val date: LocalDate, val targetMl: Int, val reason: String?, val updatedAt: Instant)

data class ActivityAdjustment(
    val source: ActivitySource,
    /** Health Connect record id, or `manual-<uuid>`. */
    val sourceRecordId: String,
    val date: LocalDate,
    val minutes: Int,
    val intensity: Intensity,
    /** `minutes x mlPerMinute(intensity)`, before the daily cap. */
    val extraMl: Int,
    val exerciseType: Int?,
    val updatedAt: Instant,
)

data class WeatherDay(
    val date: LocalDate,
    /** Latitude x 10, rounded (0.1 degree precision). */
    val latE1: Int,
    val lonE1: Int,
    val maxTempC: Double,
    val maxApparentTempC: Double,
    val meanRelHumidity: Double?,
    val extraMl: Int,
    val fetchedAt: Instant,
)

/** Passed to entry-change listeners after every successful write (§6.4). */
data class EntryChange(val affectedDays: Set<LocalDate>, val origin: ChangeOrigin)
