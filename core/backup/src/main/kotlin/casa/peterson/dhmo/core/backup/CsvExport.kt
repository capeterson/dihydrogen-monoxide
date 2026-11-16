package casa.peterson.dhmo.core.backup

import casa.peterson.dhmo.core.model.DailyGoal
import casa.peterson.dhmo.core.model.DailyTotal
import casa.peterson.dhmo.core.model.IntakeEntry
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * One day of the daily CSV. Mirrors the fields of `DayStat` (`:core:domain`) that the export needs;
 * `:core:backup` may not depend on `:core:domain` (TECHNICAL_PLAN §3.2).
 */
data class CsvDay(val date: LocalDate, val totalMl: Int, val entryCount: Int)

/** RFC 4180 CSV export (TECHNICAL_PLAN §11.10): CRLF, quoted when needed, `.` decimals, no BOM. */
object CsvExport {
    private const val CRLF = "\r\n"
    private const val PERCENT = 100
    private val specialChars = charArrayOf(',', '"', '\r', '\n')
    private val localFormat = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    private val entryHeader = listOf(
        "id", "timestamp_utc", "local_datetime", "utc_offset", "hydration_day", "volume_ml", "source", "source_app",
        "note", "created_at_utc", "updated_at_utc",
    )
    private val dailyHeader = listOf(
        "date", "total_ml", "goal_ml", "calculated_goal_ml", "goal_mode", "percent_of_goal", "goal_met", "entry_count",
        "first_entry_local", "last_entry_local",
    )

    /** Tombstoned entries are skipped. `source_app` is `appLabels[hcDataOrigin]`, falling back to the package name. */
    fun entries(entries: List<IntakeEntry>, appLabels: Map<String, String>, out: Appendable) {
        writeRow(out, entryHeader)
        for (e in entries) {
            if (e.deletedAt != null) continue
            writeRow(
                out,
                listOf(
                    e.id.toString(),
                    e.instant.toString(),
                    e.instant.atOffset(e.zoneOffset).toLocalDateTime().format(localFormat),
                    offsetText(e.zoneOffset),
                    e.hydrationDay.toString(),
                    e.volumeMl.toString(),
                    e.source.name,
                    safeText(e.hcDataOrigin?.let { appLabels[it] ?: it }.orEmpty()),
                    safeText(e.note.orEmpty()),
                    e.createdAt.toString(),
                    e.updatedAt.toString(),
                ),
            )
        }
    }

    /**
     * One row per element of [days] (the caller supplies the contiguous range, including no-log days).
     * `first_entry_local` / `last_entry_local` are rendered in [zone], because [DailyTotal] carries no offset.
     */
    fun dailyTotals(
        days: List<CsvDay>,
        goals: Map<LocalDate, DailyGoal>,
        totals: Map<LocalDate, DailyTotal>,
        out: Appendable,
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        writeRow(out, dailyHeader)
        for (day in days) {
            val goal = goals[day.date]
            val target = goal?.targetMl?.takeIf { it > 0 }
            val total = totals[day.date]?.takeIf { day.entryCount > 0 }
            writeRow(
                out,
                listOf(
                    day.date.toString(),
                    day.totalMl.toString(),
                    goal?.targetMl?.toString().orEmpty(),
                    goal?.calculatedMl?.toString().orEmpty(),
                    goal?.mode?.name.orEmpty(),
                    target?.let { percent(day.totalMl, it) }.orEmpty(),
                    target?.let { (day.totalMl >= it).toString() }.orEmpty(),
                    day.entryCount.toString(),
                    total?.firstAt?.atZone(zone)?.toLocalDateTime()?.format(localFormat).orEmpty(),
                    total?.lastAt?.atZone(zone)?.toLocalDateTime()?.format(localFormat).orEmpty(),
                ),
            )
        }
    }

    private fun percent(totalMl: Int, targetMl: Int): String =
        BigDecimal(totalMl).multiply(BigDecimal(PERCENT)).divide(BigDecimal(targetMl), 1, RoundingMode.HALF_UP).toPlainString()

    private fun offsetText(offset: ZoneOffset): String = if (offset.totalSeconds == 0) "+00:00" else offset.id

    private fun writeRow(out: Appendable, fields: List<String>) {
        fields.forEachIndexed { index, field ->
            if (index > 0) out.append(',')
            out.append(escape(field))
        }
        out.append(CRLF)
    }

    // Quoting CSV alone does not prevent spreadsheet applications from executing cell formulas.
    // Apply this only to untrusted text, so numeric values and UTC offsets retain their types.
    private fun safeText(field: String): String {
        val first = field.dropWhile { it.isWhitespace() }.firstOrNull()
        val dangerous = first in listOf('=', '+', '-', '@') || field.firstOrNull() in listOf('\t', '\r', '\n')
        return if (dangerous) "'" + field else field
    }

    private fun escape(field: String): String = if (field.startsWith("'") || field.indexOfAny(specialChars) >= 0) {
        '"' + field.replace("\"", "\"\"") + '"'
    } else {
        field
    }
}
