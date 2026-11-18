package casa.peterson.dhmo.sync.healthconnect.engine

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Volume
import androidx.room.withTransaction
import casa.peterson.dhmo.core.common.ZoneProvider
import casa.peterson.dhmo.core.data.repository.EntryChangeListener
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.usecase.goal.SnapshotDailyGoalUseCase
import casa.peterson.dhmo.core.database.DhmoDatabase
import casa.peterson.dhmo.core.database.entity.ActivityAdjustmentEntity
import casa.peterson.dhmo.core.database.entity.HcOp
import casa.peterson.dhmo.core.database.entity.IntakeEntryEntity
import casa.peterson.dhmo.core.database.entity.ProfileSnapshotEntity
import casa.peterson.dhmo.core.database.entity.SyncStateEntity
import casa.peterson.dhmo.core.domain.goal.GoalPolicy
import casa.peterson.dhmo.core.domain.time.hydrationDay
import casa.peterson.dhmo.core.model.ActivitySource
import casa.peterson.dhmo.core.model.ChangeOrigin
import casa.peterson.dhmo.core.model.EntryChange
import casa.peterson.dhmo.core.model.ProfileSource
import casa.peterson.dhmo.core.model.Source
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.floor
import kotlin.reflect.KClass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What one sync run found: permissions that were revoked and the hydration days whose data changed. */
data class SyncReport(val missingPermissions: Set<String>, val changedDays: Set<LocalDate>, val syncedAt: Instant)

/**
 * One Health Connect sync run (TECHNICAL_PLAN §10.5): push queued writes, pull changes from other apps, and read
 * the daily aggregates. A mutex lets only one run happen at a time. Failures propagate so the caller can map them
 * to a state or a retry.
 */
@Suppress("TooManyFunctions") // one sync run, split by phase: push, pull, apply, reconcile, aggregates
@Singleton
class HealthConnectSyncEngine @Inject internal constructor(
    private val clients: HealthConnectClientProvider,
    private val database: DhmoDatabase,
    private val settings: SettingsRepository,
    private val policy: GoalPolicy,
    /** A [Provider]: building the goal reads the Health Connect controller, which owns this engine. */
    private val snapshot: Provider<SnapshotDailyGoalUseCase>,
    private val listeners: Provider<Set<@JvmSuppressWildcards EntryChangeListener>>,
    private val zones: ZoneProvider,
    private val clock: Clock,
    @param:ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private val intakeDao = database.intakeDao()
    private val hcDao = database.hcSyncDao()
    private val activityDao = database.activityDao()
    private val healthDao = database.healthDailyDao()
    private val profileDao = database.profileDao()

    suspend fun sync(): SyncReport = mutex.withLock {
        val client = clients.get()
        val current = settings.current()
        val granted = client.permissionController.getGrantedPermissions()
        val missing = HcPermissions.missing(current.healthConnect, granted)
        val eff = HcPermissions.effective(current.healthConnect, granted)
        val changed = mutableSetOf<LocalDate>()
        if (eff.any) {
            push(client, eff)
            pull(client, eff, changed)
            if (eff.stepsAndCalories) readAggregates(client)
        }
        val now = clock.instant()
        hcDao.putState(SyncStateEntity(LAST_SYNC, now.toString(), now))
        if (changed.isNotEmpty()) announce(changed)
        SyncReport(missing, changed, now)
    }

    suspend fun lastSyncAt(): Instant? = hcDao.getState(LAST_SYNC)?.let { runCatching { Instant.parse(it) }.getOrNull() }

    // region push

    private suspend fun push(client: HealthConnectClient, eff: HcPermissions.Effective) {
        if (!eff.write) return
        while (true) {
            val ops = hcDao.nextOps(PUSH_BATCH)
            if (ops.isEmpty()) return
            val (dead, live) = ops.partition { it.attempts >= MAX_ATTEMPTS }
            if (dead.isNotEmpty()) hcDao.deleteOps(dead.map { it.id })
            val upserts = live.filter { it.op == HcOp.UPSERT }
            val deletes = live.filter { it.op == HcOp.DELETE }
            try {
                pushUpserts(client, upserts.map { it.entryId to it.id })
                for (op in deletes) {
                    client.deleteRecords(HydrationRecord::class, emptyList(), listOf(op.entryId.toString()))
                    hcDao.deleteOps(listOf(op.id))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                live.forEach { hcDao.markFailed(it.id, e.javaClass.simpleName) }
                throw e
            }
        }
    }

    private suspend fun pushUpserts(client: HealthConnectClient, ops: List<Pair<UUID, Long>>) {
        if (ops.isEmpty()) return
        val entries = ops.mapNotNull { (entryId, opId) ->
            val entry = intakeDao.getById(entryId)
            if (entry == null || entry.deletedAt != null || entry.source == Source.HEALTH_CONNECT_IMPORT) {
                hcDao.deleteOps(listOf(opId))
                null
            } else {
                entry to opId
            }
        }
        if (entries.isEmpty()) return
        val records = entries.map { (entry, _) -> toRecord(entry) }
        val response = client.insertRecords(records)
        entries.forEachIndexed { index, (entry, opId) ->
            response.recordIdsList.getOrNull(index)?.let { intakeDao.setHcRecordId(entry.id, it) }
            hcDao.deleteOps(listOf(opId))
        }
    }

    /** One-second interval: the client rejects `startTime == endTime` (§10.5 note). */
    private fun toRecord(entry: IntakeEntryEntity): HydrationRecord {
        val offset = ZoneOffset.ofTotalSeconds(entry.zoneOffsetSeconds)
        return HydrationRecord(
            startTime = entry.instant,
            startZoneOffset = offset,
            endTime = entry.instant.plusSeconds(1),
            endZoneOffset = offset,
            volume = Volume.milliliters(entry.volumeMl.toDouble()),
            metadata = Metadata.manualEntry(
                clientRecordId = entry.id.toString(),
                clientRecordVersion = entry.updatedAt.toEpochMilli(),
            ),
        )
    }

    // endregion

    // region pull

    @Suppress("ReturnCount")
    private suspend fun pull(client: HealthConnectClient, eff: HcPermissions.Effective, changed: MutableSet<LocalDate>) {
        val types = buildSet<KClass<out Record>> {
            if (eff.importHydration) add(HydrationRecord::class)
            if (eff.weight) add(WeightRecord::class)
            if (eff.exercise) add(ExerciseSessionRecord::class)
        }
        if (types.isEmpty()) return
        val typeKey = types.mapNotNull { it.simpleName }.sorted().joinToString(",")
        val token = hcDao.getState(TOKEN)?.takeIf { hcDao.getState(TOKEN_TYPES) == typeKey }
        if (token == null) {
            freshToken(client, types, typeKey).also { reconcile(client, eff, types, changed) }
            return
        }
        var next: String = token
        do {
            val response = client.getChanges(next)
            if (response.changesTokenExpired) {
                freshToken(client, types, typeKey)
                reconcile(client, eff, types, changed)
                return
            }
            ensureSources(response.changes.filterIsInstance<UpsertionChange>().map { it.record })
            val sources = settings.current().healthConnect.importSources
            val pinWeight = settings.current().goal.pinned.pinWeight
            val dayStart = dayStart()
            database.withTransaction {
                for (change in response.changes) {
                    when (change) {
                        is UpsertionChange -> applyUpsert(change.record, eff, sources, pinWeight, dayStart, changed)
                        is DeletionChange -> applyDeletion(change.recordId, changed)
                    }
                }
                putToken(response.nextChangesToken, typeKey)
            }
            next = response.nextChangesToken
        } while (response.hasMore)
    }

    private suspend fun freshToken(client: HealthConnectClient, types: Set<KClass<out Record>>, typeKey: String) {
        putToken(client.getChangesToken(ChangesTokenRequest(types)), typeKey)
    }

    private suspend fun putToken(token: String, typeKey: String) {
        val now = clock.instant()
        hcDao.putState(SyncStateEntity(TOKEN, token, now))
        hcDao.putState(SyncStateEntity(TOKEN_TYPES, typeKey, now))
    }

    /** A source app seen for the first time is added as included, so the user can switch it off later (§10.5). */
    private suspend fun ensureSources(records: List<Record>) {
        val own = context.packageName
        val seen = records.filterIsInstance<HydrationRecord>().map { it.metadata.dataOrigin.packageName }.filter { it != own }.toSet()
        if (seen.isEmpty()) return
        val known = settings.current().healthConnect.importSources.keys
        val fresh = seen - known
        if (fresh.isNotEmpty()) {
            settings.update { s ->
                s.copy(
                    healthConnect = s.healthConnect.copy(
                        importSources =
                            s.healthConnect.importSources + fresh.associateWith { true },
                    ),
                )
            }
        }
    }

    private suspend fun applyUpsert(
        record: Record,
        eff: HcPermissions.Effective,
        sources: Map<String, Boolean>,
        pinWeight: Boolean,
        dayStart: LocalTime,
        changed: MutableSet<LocalDate>,
    ) {
        when (record) {
            is HydrationRecord -> if (eff.importHydration) importHydration(record, sources, dayStart, changed)
            is WeightRecord -> if (eff.weight && !pinWeight) importWeight(record, changed)
            is ExerciseSessionRecord -> if (eff.exercise) importExercise(record, dayStart, changed)
        }
    }

    @Suppress("ReturnCount")
    private suspend fun importHydration(
        record: HydrationRecord,
        sources: Map<String, Boolean>,
        dayStart: LocalTime,
        changed: MutableSet<LocalDate>,
    ) {
        val origin = record.metadata.dataOrigin.packageName
        if (origin == context.packageName || sources[origin] == false) return
        val ml = floor(record.volume.inMilliliters + HALF).toInt()
        if (ml < MIN_IMPORT_ML || ml > MAX_IMPORT_ML) return
        val offset = record.startZoneOffset ?: zones.current().rules.getOffset(record.startTime)
        val day = hydrationDay(record.startTime, offset, dayStart)
        val now = clock.instant()
        val id = record.metadata.id
        val existing = intakeDao.getByHcRecordId(id)
        if (existing != null) {
            if (existing.hcAdopted) return // the user's edit wins
            intakeDao.upsert(
                existing.copy(
                    instant = record.startTime,
                    zoneOffsetSeconds = offset.totalSeconds,
                    hydrationDay = day,
                    volumeMl = ml,
                    updatedAt = now,
                    deletedAt = null,
                ),
            )
            changed += existing.hydrationDay
            changed += day
            return
        }
        val duplicate = intakeDao.importsBetween(record.startTime, record.startTime).firstOrNull {
            it.deletedAt == null && it.hcDataOrigin == origin && it.volumeMl == ml && it.hcRecordId != id
        }
        if (duplicate != null) {
            intakeDao.setHcRecordId(duplicate.id, id) // typical after a restore onto a new phone
            return
        }
        intakeDao.upsert(
            IntakeEntryEntity(
                id = UUID.randomUUID(),
                instant = record.startTime,
                zoneOffsetSeconds = offset.totalSeconds,
                hydrationDay = day,
                volumeMl = ml,
                source = Source.HEALTH_CONNECT_IMPORT,
                hcRecordId = id,
                hcDataOrigin = origin,
                createdAt = now,
                updatedAt = now,
            ),
        )
        changed += day
    }

    private suspend fun importWeight(record: WeightRecord, changed: MutableSet<LocalDate>) {
        val latest = profileDao.getAll().lastOrNull() ?: return
        val kg = Math.round(record.weight.inKilograms * TENTHS) / TENTHS
        if (abs(kg - latest.weightKg) < WEIGHT_EPSILON) return
        val offset = record.zoneOffset ?: zones.current().rules.getOffset(record.time)
        val from = record.time.atOffset(offset).toLocalDate()
        val now = clock.instant()
        profileDao.insert(
            ProfileSnapshotEntity(
                id = UUID.randomUUID(),
                effectiveFrom = from,
                birthDate = latest.birthDate,
                sex = latest.sex,
                weightKg = kg,
                heightCm = latest.heightCm,
                activityLevel = latest.activityLevel,
                climate = latest.climate,
                pregnancyStatus = latest.pregnancyStatus,
                lactating = latest.lactating,
                highAltitude = latest.highAltitude,
                source = ProfileSource.HEALTH_CONNECT,
                createdAt = now,
            ),
        )
        changed += from
    }

    private suspend fun importExercise(record: ExerciseSessionRecord, dayStart: LocalTime, changed: MutableSet<LocalDate>) {
        val minutes = Duration.between(record.startTime, record.endTime).toMinutes().toInt()
        if (minutes < MIN_EXERCISE_MINUTES) return
        val offset = record.startZoneOffset ?: zones.current().rules.getOffset(record.startTime)
        val day = hydrationDay(record.startTime, offset, dayStart)
        val intensity = intensityOf(record.exerciseType)
        activityDao.upsert(
            ActivityAdjustmentEntity(
                source = ActivitySource.HEALTH_CONNECT,
                sourceRecordId = record.metadata.id,
                date = day,
                minutes = minutes,
                intensity = intensity,
                extraMl = minutes * policy.mlPerMinute(intensity),
                exerciseType = record.exerciseType,
                updatedAt = clock.instant(),
            ),
        )
        changed += day
    }

    private suspend fun applyDeletion(recordId: String, changed: MutableSet<LocalDate>) {
        intakeDao.getByHcRecordId(recordId)?.let { row ->
            if (row.hcAdopted) {
                intakeDao.setHcRecordId(row.id, null)
            } else if (row.deletedAt == null) {
                intakeDao.softDelete(row.id, clock.instant())
                changed += row.hydrationDay
            }
        }
        activityDao.getAll().firstOrNull { it.source == ActivitySource.HEALTH_CONNECT && it.sourceRecordId == recordId }?.let {
            activityDao.delete(ActivitySource.HEALTH_CONNECT.name, recordId)
            changed += it.date
        }
    }

    // endregion

    // region reconcile

    /** Initial load, and the fallback when the changes token expired: read a window and drop what vanished. */
    @Suppress("CyclomaticComplexMethod")
    private suspend fun reconcile(
        client: HealthConnectClient,
        eff: HcPermissions.Effective,
        types: Set<KClass<out Record>>,
        changed: MutableSet<LocalDate>,
    ) {
        val now = clock.instant()
        val from = now.minus(Duration.ofDays(if (eff.history) HISTORY_DAYS else WINDOW_DAYS))
        // The end is exclusive and clocks drift, so look a day ahead; a drink stamped "now" must not fall outside.
        val filter = TimeRangeFilter.between(from, now.plus(Duration.ofDays(1)))
        val dayStart = dayStart()
        val pinWeight = settings.current().goal.pinned.pinWeight
        if (HydrationRecord::class in types) {
            val seen = readAll<HydrationRecord>(client, filter)
            ensureSources(seen)
            val sources = settings.current().healthConnect.importSources
            database.withTransaction {
                seen.forEach { importHydration(it, sources, dayStart, changed) }
                val seenIds = seen.map { it.metadata.id }.toSet()
                intakeDao.importsBetween(from, now)
                    .filter { it.deletedAt == null && !it.hcAdopted && it.hcRecordId !in seenIds }
                    .forEach {
                        intakeDao.softDelete(it.id, now)
                        changed += it.hydrationDay
                    }
            }
        }
        if (WeightRecord::class in types && !pinWeight) {
            readAll<WeightRecord>(client, filter).maxByOrNull { it.time }?.let { importWeight(it, changed) }
        }
        if (ExerciseSessionRecord::class in types) {
            val seen = readAll<ExerciseSessionRecord>(client, filter)
            database.withTransaction {
                seen.forEach { importExercise(it, dayStart, changed) }
                val seenIds = seen.map { it.metadata.id }.toSet()
                activityDao.getAll()
                    .filter { it.source == ActivitySource.HEALTH_CONNECT && it.sourceRecordId !in seenIds && it.updatedAt >= from }
                    .forEach {
                        activityDao.delete(ActivitySource.HEALTH_CONNECT.name, it.sourceRecordId)
                        changed += it.date
                    }
            }
        }
        hcDao.putState(SyncStateEntity(LAST_RECONCILE, now.toString(), now))
    }

    private suspend inline fun <reified T : Record> readAll(client: HealthConnectClient, filter: TimeRangeFilter): List<T> {
        val out = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val page = client.readRecords(ReadRecordsRequest(T::class, filter, pageSize = PAGE_SIZE, pageToken = pageToken))
            out += page.records
            pageToken = page.pageToken
        } while (pageToken != null)
        return out
    }

    // endregion

    private suspend fun readAggregates(client: HealthConnectClient) {
        val end = LocalDateTime.now(clock.withZone(zones.current()))
        val start = end.toLocalDate().minusDays(AGGREGATE_DAYS).atStartOfDay()
        val groups = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL, ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end),
                timeRangeSlicer = Period.ofDays(1),
            ),
        )
        val now = clock.instant()
        healthDao.upsertAll(
            groups.map {
                casa.peterson.dhmo.core.database.entity.HealthDailyEntity(
                    date = it.startTime.toLocalDate(),
                    steps = it.result[StepsRecord.COUNT_TOTAL],
                    activeKcal = it.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
                    updatedAt = now,
                )
            },
        )
    }

    /** Goal, reminders and widget follow imported data, so changed days are re-snapshotted and announced. */
    suspend fun announce(days: Set<LocalDate>) {
        days.forEach { runCatching { snapshot.get()(it) } }
        val change = EntryChange(days, ChangeOrigin.HEALTH_CONNECT)
        listeners.get().forEach { runCatching { it.onEntriesChanged(change) } }
    }

    private suspend fun dayStart(): LocalTime = LocalTime.ofSecondOfDay(settings.current().dayStartMinutes * SECONDS_PER_MINUTE)

    companion object {
        const val TOKEN = "hc.changes_token"
        const val TOKEN_TYPES = "hc.token_record_types"
        const val LAST_SYNC = "hc.last_sync_at"
        const val LAST_RECONCILE = "hc.last_full_reconcile_at"
        private const val PUSH_BATCH = 100
        private const val MAX_ATTEMPTS = 10
        private const val PAGE_SIZE = 1000
        private const val WINDOW_DAYS = 30L
        private const val HISTORY_DAYS = 365L * 5
        private const val AGGREGATE_DAYS = 14L
        private const val MIN_IMPORT_ML = 1
        private const val MAX_IMPORT_ML = 10_000
        private const val MIN_EXERCISE_MINUTES = 5
        private const val HALF = 0.5
        private const val TENTHS = 10.0
        private const val WEIGHT_EPSILON = 0.05
        private const val SECONDS_PER_MINUTE = 60L
    }
}
