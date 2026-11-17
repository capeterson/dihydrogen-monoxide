package casa.peterson.dhmo.core.data.repository

import androidx.paging.PagingData
import casa.peterson.dhmo.core.model.ChangeOrigin
import casa.peterson.dhmo.core.model.DailyTotal
import casa.peterson.dhmo.core.model.IntakeEntry
import casa.peterson.dhmo.core.model.Source
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface IntakeRepository {
    fun observeDay(day: LocalDate): Flow<List<IntakeEntry>>

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<IntakeEntry>>

    fun pagedHistory(): Flow<PagingData<IntakeEntry>>

    fun observeDailyTotal(day: LocalDate): Flow<DailyTotal?>

    fun observeDailyTotals(from: LocalDate, to: LocalDate): Flow<List<DailyTotal>>

    suspend fun dailyTotals(from: LocalDate, to: LocalDate): List<DailyTotal>

    suspend fun entries(from: LocalDate, to: LocalDate): List<IntakeEntry>

    suspend fun firstDay(): LocalDate?

    suspend fun get(id: UUID): IntakeEntry?

    suspend fun add(volumeMl: Int, source: Source, at: Instant? = null, note: String? = null): IntakeEntry

    /**
     * Logs a drink made on the watch (Wear OS, issue #1) with the id, instant and UTC offset the watch recorded, so a
     * request that arrives twice (message and queued data item) is stored once. Returns false for a known id.
     */
    suspend fun addFromWatch(id: UUID, volumeMl: Int, at: Instant, offset: ZoneOffset): Boolean

    suspend fun update(id: UUID, volumeMl: Int, at: Instant, note: String?): IntakeEntry

    suspend fun delete(id: UUID)

    suspend fun undoDelete(id: UUID)

    /** Health Connect import to editable (§10.6). */
    suspend fun adopt(id: UUID)

    /** §4.2 rule 2. */
    suspend fun rebucketFrom(oldToday: LocalDate, newDayStart: LocalTime)

    /** Called by the Health Connect engine and by restore after they write through DAOs. */
    suspend fun notifyExternalChange(days: Set<LocalDate>, origin: ChangeOrigin)
}
