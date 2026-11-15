package casa.peterson.dhmo.core.data.repository

import casa.peterson.dhmo.core.model.ActivityAdjustment
import casa.peterson.dhmo.core.model.Intensity
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Stub: the Room-backed implementation arrives with T4. */
class OfflineActivityRepository @Inject constructor() : ActivityRepository {
    override fun observeDay(day: LocalDate): Flow<List<ActivityAdjustment>> = TODO("T4")

    override suspend fun forDay(day: LocalDate): List<ActivityAdjustment> = TODO("T4")

    override suspend fun minutesPerDay(from: LocalDate, to: LocalDate): Map<LocalDate, Int> = TODO("T4")

    override suspend fun addPlanned(day: LocalDate, minutes: Int, intensity: Intensity) = TODO("T4")

    override suspend fun removePlanned(sourceRecordId: String) = TODO("T4")
}
