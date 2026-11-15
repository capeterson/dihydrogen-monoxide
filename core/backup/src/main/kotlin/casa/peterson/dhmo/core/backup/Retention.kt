package casa.peterson.dhmo.core.backup

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.IsoFields

data class BackupFileInfo(val id: String, val name: String, val createdAt: Instant, val schemaVersion: Int)

data class RetentionPlan(val keep: List<BackupFileInfo>, val delete: List<BackupFileInfo>)

/** Daily / weekly / monthly pruning over UTC periods that have backups (TECHNICAL_PLAN §11.8). */
object Retention {
    private val namePattern = Regex("""hydration-backup-v\d+-\d{8}T\d{6}Z\.json\.gz(\.enc)?""")

    fun select(
        files: List<BackupFileInfo>,
        keepDaily: Int = 7,
        keepWeekly: Int = 4,
        keepMonthly: Int = 6,
        supportedSchemaVersion: Int = 1,
    ): RetentionPlan {
        val (managed, protectedFiles) = files.partition {
            it.schemaVersion <= supportedSchemaVersion && namePattern.matches(it.name)
        }
        val sorted = managed.sortedWith(compareByDescending<BackupFileInfo> { it.createdAt }.thenByDescending { it.name })
        val keepSet = HashSet<BackupFileInfo>(protectedFiles)
        keepSet += newestPerPeriod(sorted, keepDaily) { utcDate(it) }
        keepSet += newestPerPeriod(sorted, keepWeekly) {
            utcDate(it).let { date -> date.get(IsoFields.WEEK_BASED_YEAR) to date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) }
        }
        keepSet += newestPerPeriod(sorted, keepMonthly) { utcDate(it).let { date -> date.year to date.monthValue } }
        return RetentionPlan(
            keep = files.filter { it in keepSet },
            delete = files.filter { it !in keepSet },
        )
    }

    private fun utcDate(file: BackupFileInfo): LocalDate = file.createdAt.atOffset(ZoneOffset.UTC).toLocalDate()

    private fun <K> newestPerPeriod(
        sortedNewestFirst: List<BackupFileInfo>,
        limit: Int,
        key: (BackupFileInfo) -> K,
    ): List<BackupFileInfo> {
        val seen = HashSet<K>()
        val result = ArrayList<BackupFileInfo>()
        for (file in sortedNewestFirst) {
            if (seen.size >= limit) break
            if (seen.add(key(file))) result += file
        }
        return result
    }
}
