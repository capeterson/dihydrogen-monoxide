package casa.peterson.dhmo.core.data.backup

import android.util.Log
import casa.peterson.dhmo.core.backup.BackupCodec
import casa.peterson.dhmo.core.backup.BackupDocument
import casa.peterson.dhmo.core.backup.BackupEntities
import casa.peterson.dhmo.core.backup.BackupMerge
import casa.peterson.dhmo.core.backup.toCounts
import casa.peterson.dhmo.core.data.repository.BackupRepository
import casa.peterson.dhmo.core.data.repository.IntakeRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.database.DhmoDatabase
import casa.peterson.dhmo.core.database.TransactionRunner
import casa.peterson.dhmo.core.database.dao.ActivityDao
import casa.peterson.dhmo.core.database.dao.ContainerDao
import casa.peterson.dhmo.core.database.dao.GoalDao
import casa.peterson.dhmo.core.database.dao.HcSyncDao
import casa.peterson.dhmo.core.database.dao.HealthDailyDao
import casa.peterson.dhmo.core.database.dao.IntakeDao
import casa.peterson.dhmo.core.database.dao.ProfileDao
import casa.peterson.dhmo.core.database.dao.WeatherDao
import casa.peterson.dhmo.core.database.entity.HcOp
import casa.peterson.dhmo.core.database.entity.HcPendingOpEntity
import casa.peterson.dhmo.core.database.entity.IntakeEntryEntity
import casa.peterson.dhmo.core.domain.goal.GoalPolicy
import casa.peterson.dhmo.core.model.ChangeOrigin
import casa.peterson.dhmo.core.model.Source
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.sync.RestoreMode
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Room- and DataStore-backed [BackupRepository] (TECHNICAL_PLAN §11.9).
 *
 * Every restore first writes a gzip safety snapshot of the current data to [BackupEnvironment.safetyDir] (the newest
 * [KEEP_SNAPSHOTS] are kept), applies the document in one Room transaction, applies settings, and then re-derives
 * dependent state through the entry-change listeners and [AfterRestore].
 */
class OfflineBackupRepository
@Inject
constructor(
    private val intakeDao: IntakeDao,
    private val containerDao: ContainerDao,
    private val profileDao: ProfileDao,
    private val goalDao: GoalDao,
    private val activityDao: ActivityDao,
    private val weatherDao: WeatherDao,
    private val healthDailyDao: HealthDailyDao,
    private val hcSyncDao: HcSyncDao,
    private val transactions: TransactionRunner,
    private val settings: SettingsRepository,
    private val intake: IntakeRepository,
    private val policy: GoalPolicy,
    private val environment: BackupEnvironment,
    private val afterRestore: AfterRestore,
    private val clock: Clock,
) : BackupRepository {
    override suspend fun exportDocument(): BackupDocument {
        val entities = transactions.run { readEntities() }
        return BackupDocument(
            appVersion = environment.appVersionName,
            appVersionCode = environment.appVersionCode,
            dbVersion = DhmoDatabase.VERSION,
            policyVersion = policy.version,
            createdAt = clock.instant(),
            deviceName = environment.deviceName,
            counts = toCounts(entities),
            entities = entities,
            settings = settings.current(),
        )
    }

    override suspend fun restore(doc: BackupDocument, mode: RestoreMode) {
        writeSafetySnapshot()
        when (mode) {
            RestoreMode.REPLACE -> replace(doc, sanitizeSettings = true)
            RestoreMode.MERGE -> merge(doc)
        }
    }

    override suspend fun undoLastRestore(): Boolean {
        val file = latestSafetySnapshot() ?: return false
        val doc = BackupCodec.decode(file.readBytes())
        // The snapshot was taken on this device, so its settings need no sanitising.
        replace(doc, sanitizeSettings = false)
        file.delete()
        return true
    }

    override suspend fun writeSafetySnapshot(): File {
        val dir = environment.safetyDir.apply { mkdirs() }
        val stamp = SNAPSHOT_STAMP.format(clock.instant().atOffset(ZoneOffset.UTC))
        var file = File(dir, "$SNAPSHOT_PREFIX$stamp$SNAPSHOT_SUFFIX")
        var n = 1
        while (file.exists()) file = File(dir, "$SNAPSHOT_PREFIX$stamp-${n++}$SNAPSHOT_SUFFIX")
        val tmp = File(dir, file.name + ".tmp")
        tmp.writeBytes(BackupCodec.encodeGzip(exportDocument()))
        check(tmp.renameTo(file)) { "Could not write safety snapshot" }
        snapshots().drop(KEEP_SNAPSHOTS).forEach { it.delete() }
        return file
    }

    override suspend fun latestSafetySnapshot(): File? = snapshots().firstOrNull()

    /** Newest first: by UTC stamp, then by the same-second counter suffix (`…Z-1`, `…Z-2`). */
    private fun snapshots(): List<File> = environment.safetyDir
        .listFiles { f -> f.isFile && f.name.startsWith(SNAPSHOT_PREFIX) && f.name.endsWith(SNAPSHOT_SUFFIX) }
        .orEmpty()
        .sortedWith(
            compareByDescending<File> { it.name.removePrefix(SNAPSHOT_PREFIX).substringBefore('Z') }
                .thenByDescending { it.name.removeSuffix(SNAPSHOT_SUFFIX).substringAfter("Z-", "0").toIntOrNull() ?: 0 },
        )

    private suspend fun readEntities(): BackupEntities = BackupEntities(
        intakeEntries = intakeDao.getAllIncludingDeleted().sortedWith(compareBy({ it.instant }, { it.id })).map { it.toDto() },
        containers = containerDao.getAllIncludingDeleted().sortedWith(compareBy({ it.sortOrder }, { it.id })).map { it.toDto() },
        profileSnapshots = profileDao.getAll().map { it.toDto() },
        dailyGoals = goalDao.getAllGoals().sortedBy { it.date }.map { it.toDto() },
        dayOverrides = goalDao.getAllOverridesIncludingDeleted().sortedBy { it.date }.map { it.toDto() },
        activityAdjustments = activityDao.getAll().sortedWith(compareBy({ it.date }, { it.sourceRecordId })).map { it.toDto() },
        weatherDays = weatherDao.getAll().sortedBy { it.date }.map { it.toDto() },
        healthDaily = healthDailyDao.getAll().sortedBy { it.date }.map { it.toDto() },
    )

    private suspend fun replace(doc: BackupDocument, sanitizeSettings: Boolean) {
        val rows = doc.entities.toRows()
        logSkipped(rows.skipped)
        val affected = transactions.run {
            val oldDays = intakeDao.getAllIncludingDeleted().map { it.hydrationDay }
            intakeDao.deleteAll()
            containerDao.deleteAll()
            profileDao.deleteAll()
            goalDao.deleteAllGoals()
            goalDao.deleteAllOverrides()
            activityDao.deleteAll()
            weatherDao.deleteAll()
            healthDailyDao.deleteAll()
            hcSyncDao.clearOps()
            hcSyncDao.clearState()
            write(rows)
            (oldDays + rows.intakeEntries.map { it.hydrationDay }).toSet()
        }
        val local = settings.current()
        settings.update { if (sanitizeSettings) sanitize(doc.settings, local) else doc.settings }
        finish(affected)
    }

    private suspend fun merge(doc: BackupDocument) {
        val writeHc = settings.current().healthConnect.writeHydration
        val affected = transactions.run {
            val localIntake = intakeDao.getAllIncludingDeleted()
            val local = readEntities()
            val merged = BackupMerge.merge(local, doc.entities)
            val rows = merged.toRows(localIntake.mapNotNull { e -> e.hcRecordId?.let { e.id to it } }.toMap())
            logSkipped(rows.skipped)
            write(rows)
            val before = localIntake.associateBy { it.id }
            val changed = rows.intakeEntries.filter { it.withoutHcId() != before[it.id]?.withoutHcId() }
            if (writeHc) {
                val now = clock.instant()
                changed.filter { it.source != Source.HEALTH_CONNECT_IMPORT }.forEach {
                    hcSyncDao.enqueue(
                        HcPendingOpEntity(
                            entryId = it.id,
                            op = if (it.deletedAt ==
                                null
                            ) {
                                HcOp.UPSERT
                            } else {
                                HcOp.DELETE
                            },
                            enqueuedAt = now,
                        ),
                    )
                }
            }
            changed.flatMap { listOfNotNull(it.hydrationDay, before[it.id]?.hydrationDay) }.toSet()
        }
        finish(affected)
    }

    private suspend fun write(rows: BackupRows) {
        intakeDao.upsertAll(rows.intakeEntries)
        containerDao.upsertAll(rows.containers)
        profileDao.insertAll(rows.profileSnapshots)
        goalDao.upsertGoals(rows.dailyGoals)
        goalDao.upsertOverrides(rows.dayOverrides)
        activityDao.upsertAll(rows.activityAdjustments)
        weatherDao.upsertAll(rows.weatherDays)
        healthDailyDao.upsertAll(rows.healthDaily)
    }

    private suspend fun finish(affectedDays: Set<LocalDate>) {
        intake.notifyExternalChange(affectedDays, ChangeOrigin.RESTORE)
        afterRestore.onRestored()
    }

    private fun IntakeEntryEntity.withoutHcId() = copy(hcRecordId = null)

    private fun logSkipped(skipped: Int) {
        if (skipped > 0) Log.w(TAG, "Skipped $skipped backup rows with unknown values")
    }

    companion object {
        private const val TAG = "BackupRepository"
        const val KEEP_SNAPSHOTS = 3
        private const val SNAPSHOT_PREFIX = "pre-restore-"
        private const val SNAPSHOT_SUFFIX = ".json.gz"
        private val SNAPSHOT_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

        /**
         * Settings sanitising on replace (§11.9): Health Connect switches off (permissions are per device, the import
         * source list is kept), Drive backup configuration kept from this device, everything else from the backup.
         */
        fun sanitize(incoming: AppSettings, local: AppSettings): AppSettings = incoming.copy(
            backup = local.backup,
            healthConnect = incoming.healthConnect.copy(
                writeHydration = false,
                importHydration = false,
                syncWeight = false,
                useExercise = false,
                readStepsAndCalories = false,
                backgroundSync = false,
                importHistory = false,
            ),
        )
    }
}
