package casa.peterson.dhmo.core.data.repository

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import casa.peterson.dhmo.core.common.ZoneProvider
import casa.peterson.dhmo.core.common.suspendRunCatching
import casa.peterson.dhmo.core.data.mapper.toEntity
import casa.peterson.dhmo.core.data.mapper.toModel
import casa.peterson.dhmo.core.data.usecase.intake.EntryNotFoundException
import casa.peterson.dhmo.core.data.usecase.intake.IntakeLimits
import casa.peterson.dhmo.core.database.TransactionRunner
import casa.peterson.dhmo.core.database.dao.DailyTotalDao
import casa.peterson.dhmo.core.database.dao.HcSyncDao
import casa.peterson.dhmo.core.database.dao.IntakeDao
import casa.peterson.dhmo.core.database.entity.HcOp
import casa.peterson.dhmo.core.database.entity.HcPendingOpEntity
import casa.peterson.dhmo.core.database.entity.IntakeEntryEntity
import casa.peterson.dhmo.core.domain.time.hydrationDay
import casa.peterson.dhmo.core.model.ChangeOrigin
import casa.peterson.dhmo.core.model.DailyTotal
import casa.peterson.dhmo.core.model.EntryChange
import casa.peterson.dhmo.core.model.IntakeEntry
import casa.peterson.dhmo.core.model.Source
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * Room-backed [IntakeRepository] (§4.2, §6.4).
 *
 * Every write bucketizes with the current day start, runs in one transaction together with its
 * `hc_pending_op` row, and then calls every [EntryChangeListener] once with the affected days.
 */
class OfflineIntakeRepository
@Inject
constructor(
    private val intakeDao: IntakeDao,
    private val dailyTotalDao: DailyTotalDao,
    private val hcSyncDao: HcSyncDao,
    private val transactions: TransactionRunner,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
    private val listeners: Set<@JvmSuppressWildcards EntryChangeListener>,
) : IntakeRepository {
    override fun observeDay(day: LocalDate): Flow<List<IntakeEntry>> = intakeDao.observeDay(day).map { rows -> rows.map { it.toModel() } }

    override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<IntakeEntry>> =
        intakeDao.observeRange(from, to).map { rows -> rows.map { it.toModel() } }

    override fun pagedHistory(): Flow<PagingData<IntakeEntry>> =
        Pager(PagingConfig(pageSize = HISTORY_PAGE_SIZE)) { intakeDao.pagingSource() }
            .flow
            .map { paging -> paging.map { it.toModel() } }

    override fun observeDailyTotal(day: LocalDate): Flow<DailyTotal?> = dailyTotalDao.observeDay(day).map { it?.toModel() }

    override fun observeDailyTotals(from: LocalDate, to: LocalDate): Flow<List<DailyTotal>> =
        dailyTotalDao.observeRange(from, to).map { rows -> rows.map { it.toModel() } }

    override suspend fun dailyTotals(from: LocalDate, to: LocalDate): List<DailyTotal> =
        dailyTotalDao.getRange(from, to).map { it.toModel() }

    override suspend fun entries(from: LocalDate, to: LocalDate): List<IntakeEntry> = intakeDao.getRange(from, to).map { it.toModel() }

    override suspend fun firstDay(): LocalDate? = dailyTotalDao.firstDay()

    /** Returns the row even when it is a tombstone, so that undo can find it. */
    override suspend fun get(id: UUID): IntakeEntry? = intakeDao.getById(id)?.toModel()

    override suspend fun add(volumeMl: Int, source: Source, at: Instant?, note: String?): IntakeEntry {
        requireValid(volumeMl, note)
        val current = settings.current()
        val now = clock.instant()
        val instant = at ?: now
        val offset = zoneProvider.current().rules.getOffset(instant)
        val entity = IntakeEntryEntity(
            id = UUID.randomUUID(),
            instant = instant,
            zoneOffsetSeconds = offset.totalSeconds,
            hydrationDay = hydrationDay(instant, offset, dayStartOf(current.dayStartMinutes)),
            volumeMl = volumeMl,
            source = source,
            note = note,
            createdAt = now,
            updatedAt = now,
        )
        transactions.run {
            intakeDao.upsert(entity)
            enqueueIfWriting(current.healthConnect.writeHydration, entity, HcOp.UPSERT, now)
        }
        notifyListeners(setOf(entity.hydrationDay), ChangeOrigin.LOCAL)
        return entity.toModel()
    }

    override suspend fun addFromWatch(id: UUID, volumeMl: Int, at: Instant, offset: ZoneOffset): Boolean {
        requireValid(volumeMl, null)
        val current = settings.current()
        val now = clock.instant()
        val entity = IntakeEntryEntity(
            id = id,
            instant = at,
            zoneOffsetSeconds = offset.totalSeconds,
            hydrationDay = hydrationDay(at, offset, dayStartOf(current.dayStartMinutes)),
            volumeMl = volumeMl,
            source = Source.WEAR,
            createdAt = now,
            updatedAt = now,
        )
        val inserted = transactions.run {
            if (intakeDao.getById(id) != null) return@run false
            intakeDao.upsert(entity)
            enqueueIfWriting(current.healthConnect.writeHydration, entity, HcOp.UPSERT, now)
            true
        }
        if (inserted) notifyListeners(setOf(entity.hydrationDay), ChangeOrigin.LOCAL)
        return inserted
    }

    override suspend fun update(id: UUID, volumeMl: Int, at: Instant, note: String?): IntakeEntry {
        requireValid(volumeMl, note)
        val current = settings.current()
        val now = clock.instant()
        val updated = transactions.run {
            val existing = liveRow(id)
            val moved = at != existing.instant
            val offsetSeconds = if (moved) zoneProvider.current().rules.getOffset(at).totalSeconds else existing.zoneOffsetSeconds
            val day = if (moved) {
                hydrationDay(at, ZoneOffset.ofTotalSeconds(offsetSeconds), dayStartOf(current.dayStartMinutes))
            } else {
                existing.hydrationDay
            }
            val entity = existing.copy(
                instant = at,
                zoneOffsetSeconds = offsetSeconds,
                hydrationDay = day,
                volumeMl = volumeMl,
                note = note,
                updatedAt = now,
            )
            intakeDao.upsert(entity)
            enqueueIfWriting(current.healthConnect.writeHydration, entity, HcOp.UPSERT, now)
            existing.hydrationDay to entity
        }
        notifyListeners(setOf(updated.first, updated.second.hydrationDay), ChangeOrigin.LOCAL)
        return updated.second.toModel()
    }

    override suspend fun delete(id: UUID) {
        val writing = settings.current().healthConnect.writeHydration
        val now = clock.instant()
        val day = transactions.run {
            val existing = intakeDao.getById(id) ?: throw EntryNotFoundException(id)
            if (existing.deletedAt != null) return@run null
            intakeDao.softDelete(id, now)
            enqueueIfWriting(writing, existing, HcOp.DELETE, now)
            existing.hydrationDay
        }
        if (day != null) notifyListeners(setOf(day), ChangeOrigin.LOCAL)
    }

    override suspend fun undoDelete(id: UUID) {
        val writing = settings.current().healthConnect.writeHydration
        val now = clock.instant()
        val day = transactions.run {
            val existing = intakeDao.getById(id) ?: throw EntryNotFoundException(id)
            if (existing.deletedAt == null) return@run null
            intakeDao.restore(id, now)
            enqueueIfWriting(writing, existing, HcOp.UPSERT, now)
            existing.hydrationDay
        }
        if (day != null) notifyListeners(setOf(day), ChangeOrigin.LOCAL)
    }

    override suspend fun adopt(id: UUID) {
        val now = clock.instant()
        val day = transactions.run {
            val existing = liveRow(id)
            require(existing.source == Source.HEALTH_CONNECT_IMPORT) { "Only Health Connect imports can be adopted" }
            if (existing.hcAdopted) return@run null
            intakeDao.upsert(existing.copy(hcAdopted = true, updatedAt = now))
            existing.hydrationDay
        }
        if (day != null) notifyListeners(setOf(day), ChangeOrigin.LOCAL)
    }

    override suspend fun rebucketFrom(oldToday: LocalDate, newDayStart: LocalTime) {
        val affected = transactions.run {
            buildSet {
                intakeDao.entriesFromDay(oldToday).forEach { row ->
                    val day = hydrationDay(row.instant, ZoneOffset.ofTotalSeconds(row.zoneOffsetSeconds), newDayStart)
                    if (day != row.hydrationDay) {
                        intakeDao.updateHydrationDay(row.id, day)
                        add(row.hydrationDay)
                        add(day)
                    }
                }
            }
        }
        notifyListeners(affected, ChangeOrigin.LOCAL)
    }

    override suspend fun notifyExternalChange(days: Set<LocalDate>, origin: ChangeOrigin) {
        notifyListeners(days, origin)
    }

    private suspend fun liveRow(id: UUID): IntakeEntryEntity {
        val row = intakeDao.getById(id)
        if (row == null || row.deletedAt != null) throw EntryNotFoundException(id)
        return row
    }

    private suspend fun enqueueIfWriting(writing: Boolean, entry: IntakeEntryEntity, op: HcOp, now: Instant) {
        if (writing && entry.source != Source.HEALTH_CONNECT_IMPORT) {
            hcSyncDao.enqueue(HcPendingOpEntity(entryId = entry.id, op = op, enqueuedAt = now))
        }
    }

    /** One failing listener never fails the write or starves the others. */
    private suspend fun notifyListeners(days: Set<LocalDate>, origin: ChangeOrigin) {
        if (days.isEmpty() || listeners.isEmpty()) return
        val change = EntryChange(days, origin)
        supervisorScope {
            listeners.forEach { listener ->
                launch {
                    suspendRunCatching { listener.onEntriesChanged(change) }
                        .onFailure { Log.w(TAG, "Entry change listener failed", it) }
                }
            }
        }
    }

    private fun requireValid(volumeMl: Int, note: String?) {
        require(volumeMl in IntakeLimits.MIN_VOLUME_ML..IntakeLimits.MAX_VOLUME_ML) { "Volume out of range: $volumeMl ml" }
        require(note == null || note.length <= IntakeLimits.MAX_NOTE_LENGTH) { "Note is too long" }
    }

    private fun dayStartOf(minutes: Int): LocalTime = LocalTime.ofSecondOfDay(minutes * SECONDS_PER_MINUTE)

    private companion object {
        const val TAG = "IntakeRepository"
        const val HISTORY_PAGE_SIZE = 50
        const val SECONDS_PER_MINUTE = 60L
    }
}
