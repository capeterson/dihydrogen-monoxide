package casa.peterson.dhmo.core.data.repository

import casa.peterson.dhmo.core.data.mapper.toModel
import casa.peterson.dhmo.core.database.dao.ActivityDao
import casa.peterson.dhmo.core.database.entity.ActivityAdjustmentEntity
import casa.peterson.dhmo.core.model.ActivityAdjustment
import casa.peterson.dhmo.core.model.ActivitySource
import casa.peterson.dhmo.core.model.Intensity
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room-backed [ActivityRepository]. */
class OfflineActivityRepository
@Inject
constructor(private val dao: ActivityDao, private val clock: Clock) : ActivityRepository {
    override fun observeDay(day: LocalDate): Flow<List<ActivityAdjustment>> = dao.observeDay(day).map { rows -> rows.map { it.toModel() } }

    override suspend fun forDay(day: LocalDate): List<ActivityAdjustment> = dao.getDay(day).map { it.toModel() }

    override suspend fun minutesPerDay(from: LocalDate, to: LocalDate): Map<LocalDate, Int> =
        dao.minutesPerDay(from, to).associate { it.date to it.minutes }

    override suspend fun addPlanned(day: LocalDate, minutes: Int, intensity: Intensity) {
        require(minutes > 0) { "Minutes must be positive" }
        dao.upsert(
            ActivityAdjustmentEntity(
                source = ActivitySource.MANUAL_PLANNED,
                sourceRecordId = "$MANUAL_PREFIX${UUID.randomUUID()}",
                date = day,
                minutes = minutes,
                intensity = intensity,
                extraMl = minutes * mlPerMinute(intensity),
                updatedAt = clock.instant(),
            ),
        )
    }

    override suspend fun removePlanned(sourceRecordId: String) {
        dao.delete(ActivitySource.MANUAL_PLANNED.name, sourceRecordId)
    }

    /** §7.2: light 6, moderate 12, vigorous 18 ml per minute. */
    private fun mlPerMinute(intensity: Intensity): Int = when (intensity) {
        Intensity.LIGHT -> LIGHT_ML_PER_MINUTE
        Intensity.MODERATE -> MODERATE_ML_PER_MINUTE
        Intensity.VIGOROUS -> VIGOROUS_ML_PER_MINUTE
    }

    private companion object {
        const val MANUAL_PREFIX = "manual-"
        const val LIGHT_ML_PER_MINUTE = 6
        const val MODERATE_ML_PER_MINUTE = 12
        const val VIGOROUS_ML_PER_MINUTE = 18
    }
}
