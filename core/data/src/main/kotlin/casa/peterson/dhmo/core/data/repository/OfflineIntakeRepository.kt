package casa.peterson.dhmo.core.data.repository

import androidx.paging.PagingData
import casa.peterson.dhmo.core.model.ChangeOrigin
import casa.peterson.dhmo.core.model.DailyTotal
import casa.peterson.dhmo.core.model.IntakeEntry
import casa.peterson.dhmo.core.model.Source
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Stub: the Room-backed implementation arrives with T4. */
class OfflineIntakeRepository @Inject constructor() : IntakeRepository {
    override fun observeDay(day: LocalDate): Flow<List<IntakeEntry>> = TODO("T4")

    override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<IntakeEntry>> = TODO("T4")

    override fun pagedHistory(): Flow<PagingData<IntakeEntry>> = TODO("T4")

    override fun observeDailyTotal(day: LocalDate): Flow<DailyTotal?> = TODO("T4")

    override fun observeDailyTotals(from: LocalDate, to: LocalDate): Flow<List<DailyTotal>> = TODO("T4")

    override suspend fun dailyTotals(from: LocalDate, to: LocalDate): List<DailyTotal> = TODO("T4")

    override suspend fun entries(from: LocalDate, to: LocalDate): List<IntakeEntry> = TODO("T4")

    override suspend fun firstDay(): LocalDate? = TODO("T4")

    override suspend fun get(id: UUID): IntakeEntry? = TODO("T4")

    override suspend fun add(volumeMl: Int, source: Source, at: Instant?, note: String?): IntakeEntry = TODO("T4")

    override suspend fun update(id: UUID, volumeMl: Int, at: Instant, note: String?): IntakeEntry = TODO("T4")

    override suspend fun delete(id: UUID) = TODO("T4")

    override suspend fun undoDelete(id: UUID) = TODO("T4")

    override suspend fun adopt(id: UUID) = TODO("T4")

    override suspend fun rebucketFrom(oldToday: LocalDate, newDayStart: LocalTime) = TODO("T4")

    override suspend fun notifyExternalChange(days: Set<LocalDate>, origin: ChangeOrigin) = TODO("T4")
}
