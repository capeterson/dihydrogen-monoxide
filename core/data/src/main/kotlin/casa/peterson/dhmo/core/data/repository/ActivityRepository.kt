package casa.peterson.dhmo.core.data.repository

import casa.peterson.dhmo.core.model.ActivityAdjustment
import casa.peterson.dhmo.core.model.Intensity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface ActivityRepository {
    fun observeDay(day: LocalDate): Flow<List<ActivityAdjustment>>

    suspend fun forDay(day: LocalDate): List<ActivityAdjustment>

    suspend fun minutesPerDay(from: LocalDate, to: LocalDate): Map<LocalDate, Int>

    suspend fun addPlanned(day: LocalDate, minutes: Int, intensity: Intensity)

    suspend fun removePlanned(sourceRecordId: String)
}
