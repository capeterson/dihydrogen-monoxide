package casa.peterson.dhmo.core.testing

import androidx.paging.PagingData
import casa.peterson.dhmo.core.common.ZoneProvider
import casa.peterson.dhmo.core.data.repository.IntakeRepository
import casa.peterson.dhmo.core.domain.time.hydrationDay
import casa.peterson.dhmo.core.model.ChangeOrigin
import casa.peterson.dhmo.core.model.DailyTotal
import casa.peterson.dhmo.core.model.IntakeEntry
import casa.peterson.dhmo.core.model.Source
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** One call to [FakeIntakeRepository.notifyExternalChange]. */
data class ExternalChange(val days: Set<LocalDate>, val origin: ChangeOrigin)

/**
 * An in-memory [IntakeRepository] with the same bucketing rules as the Room-backed one. Entries are keyed by id and
 * include tombstones; flows only show live ones.
 */
class FakeIntakeRepository(
    private val clock: Clock = MutableClock(java.time.Instant.parse("2026-06-10T08:00:00Z")),
    private val zoneProvider: ZoneProvider = FixedZoneProvider(),
    var dayStart: LocalTime = LocalTime.MIDNIGHT,
) : IntakeRepository {
    private val rows = MutableStateFlow<Map<UUID, IntakeEntry>>(emptyMap())

    /** Every id passed to [delete], in order. */
    val deletedIds = mutableListOf<UUID>()

    /** Every call to [notifyExternalChange]. */
    val externalChanges = mutableListOf<ExternalChange>()

    val all: List<IntakeEntry> get() = rows.value.values.sortedBy { it.instant }

    /** Seeds an entry as it is, for example an import. */
    fun put(entry: IntakeEntry) {
        rows.value = rows.value + (entry.id to entry)
    }

    private fun live(): Flow<List<IntakeEntry>> = rows.map { map -> map.values.filter { it.deletedAt == null }.sortedBy { it.instant } }

    override fun observeDay(day: LocalDate): Flow<List<IntakeEntry>> = live().map { list -> list.filter { it.hydrationDay == day } }

    override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<IntakeEntry>> =
        live().map { list -> list.filter { it.hydrationDay in from..to } }

    override fun pagedHistory(): Flow<PagingData<IntakeEntry>> = live().map { list ->
        PagingData.from(
            list.sortedWith(
                compareByDescending<IntakeEntry> {
                    it.hydrationDay
                }.thenByDescending { it.instant }.thenByDescending { it.id.toString() },
            ),
        )
    }

    override fun observeDailyTotal(day: LocalDate): Flow<DailyTotal?> = observeDailyTotals(day, day).map { it.firstOrNull() }

    override fun observeDailyTotals(from: LocalDate, to: LocalDate): Flow<List<DailyTotal>> =
        live().map { list -> totals(list.filter { it.hydrationDay in from..to }) }

    override suspend fun dailyTotals(from: LocalDate, to: LocalDate): List<DailyTotal> =
        totals(liveNow().filter { it.hydrationDay in from..to })

    override suspend fun entries(from: LocalDate, to: LocalDate): List<IntakeEntry> = liveNow().filter { it.hydrationDay in from..to }

    override suspend fun firstDay(): LocalDate? = liveNow().minOfOrNull { it.hydrationDay }

    override suspend fun get(id: UUID): IntakeEntry? = rows.value[id]

    override suspend fun add(volumeMl: Int, source: Source, at: Instant?, note: String?): IntakeEntry {
        val now = clock.instant()
        val instant = at ?: now
        val offset = zoneProvider.current().rules.getOffset(instant)
        val entry = IntakeEntry(
            id = UUID.randomUUID(),
            instant = instant,
            zoneOffset = offset,
            hydrationDay = hydrationDay(instant, offset, dayStart),
            volumeMl = volumeMl,
            source = source,
            note = note,
            createdAt = now,
            updatedAt = now,
        )
        put(entry)
        return entry
    }

    override suspend fun update(id: UUID, volumeMl: Int, at: Instant, note: String?): IntakeEntry {
        val existing = checkNotNull(rows.value[id]) { "No entry $id" }
        val offset = zoneProvider.current().rules.getOffset(at)
        val updated = existing.copy(
            instant = at,
            zoneOffset = offset,
            hydrationDay = hydrationDay(at, offset, dayStart),
            volumeMl = volumeMl,
            note = note,
            updatedAt = clock.instant(),
        )
        put(updated)
        return updated
    }

    override suspend fun delete(id: UUID) {
        val existing = checkNotNull(rows.value[id]) { "No entry $id" }
        deletedIds += id
        val now = clock.instant()
        put(existing.copy(deletedAt = now, updatedAt = now))
    }

    override suspend fun undoDelete(id: UUID) {
        val existing = checkNotNull(rows.value[id]) { "No entry $id" }
        put(existing.copy(deletedAt = null, updatedAt = clock.instant()))
    }

    override suspend fun adopt(id: UUID) {
        val existing = checkNotNull(rows.value[id]) { "No entry $id" }
        put(existing.copy(hcAdopted = true, updatedAt = clock.instant()))
    }

    override suspend fun rebucketFrom(oldToday: LocalDate, newDayStart: LocalTime) {
        dayStart = newDayStart
        rows.value.values.filter { it.hydrationDay >= oldToday }.forEach {
            put(it.copy(hydrationDay = hydrationDay(it.instant, it.zoneOffset, newDayStart)))
        }
    }

    override suspend fun notifyExternalChange(days: Set<LocalDate>, origin: ChangeOrigin) {
        externalChanges += ExternalChange(days, origin)
    }

    private fun liveNow(): List<IntakeEntry> = rows.value.values.filter { it.deletedAt == null }

    private fun totals(list: List<IntakeEntry>): List<DailyTotal> = list.groupBy { it.hydrationDay }
        .toSortedMap()
        .map { (day, group) ->
            DailyTotal(
                date = day,
                totalMl = group.sumOf { it.volumeMl },
                entryCount = group.size,
                firstAt = group.minOf { it.instant },
                lastAt = group.maxOf { it.instant },
            )
        }
}
