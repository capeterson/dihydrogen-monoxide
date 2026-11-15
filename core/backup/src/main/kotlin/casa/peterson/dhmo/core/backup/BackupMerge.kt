package casa.peterson.dhmo.core.backup

import java.time.Instant

/** Pure merge of an incoming document into local data (TECHNICAL_PLAN §11.9). Local settings are kept. */
object BackupMerge {
    /** Merges [incoming] into [local]; the result is [local] with merged entities and recomputed counts. */
    fun merge(local: BackupDocument, incoming: BackupDocument): BackupDocument {
        val entities = merge(local.entities, incoming.entities)
        return local.copy(entities = entities, counts = toCounts(entities))
    }

    fun merge(local: BackupEntities, incoming: BackupEntities): BackupEntities = BackupEntities(
        intakeEntries = union(local.intakeEntries, incoming.intakeEntries, { it.id }, { it.updatedAt }),
        containers = union(local.containers, incoming.containers, { it.id }, { it.updatedAt }),
        profileSnapshots = union(local.profileSnapshots, incoming.profileSnapshots, { it.id }, { null }),
        dailyGoals = union(local.dailyGoals, incoming.dailyGoals, { it.date }, { it.updatedAt }),
        dayOverrides = union(local.dayOverrides, incoming.dayOverrides, { it.date }, { it.updatedAt }),
        activityAdjustments = union(
            local.activityAdjustments,
            incoming.activityAdjustments,
            { it.source to it.sourceRecordId },
            { it.updatedAt },
        ),
        weatherDays = union(local.weatherDays, incoming.weatherDays, { it.date }, { it.fetchedAt }),
        healthDaily = union(local.healthDaily, incoming.healthDaily, { it.date }, { it.updatedAt }),
    )

    /**
     * Union by key. On a clash the row with the strictly later stamp wins as a whole; equal stamps (and a
     * null stamp, for immutable rows) keep local. Local order is preserved, new incoming rows are appended.
     */
    private fun <T, K> union(local: List<T>, incoming: List<T>, key: (T) -> K, stamp: (T) -> Instant?): List<T> {
        val result = LinkedHashMap<K, T>()
        local.forEach { result[key(it)] = it }
        for (row in incoming) {
            val k = key(row)
            val existing = result[k]
            if (existing == null) {
                result[k] = row
            } else {
                val theirs = stamp(row)
                val ours = stamp(existing)
                if (theirs != null && ours != null && theirs.isAfter(ours)) result[k] = row
            }
        }
        return result.values.toList()
    }
}
