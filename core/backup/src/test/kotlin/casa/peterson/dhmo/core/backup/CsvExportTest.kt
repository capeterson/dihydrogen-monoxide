package casa.peterson.dhmo.core.backup

import casa.peterson.dhmo.core.model.ClampFlag
import casa.peterson.dhmo.core.model.DailyGoal
import casa.peterson.dhmo.core.model.DailyTotal
import casa.peterson.dhmo.core.model.GoalBreakdown
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.model.IntakeEntry
import casa.peterson.dhmo.core.model.Source
import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Test

class CsvExportTest {
    private val id = UUID.fromString("5f0c1a52-8f2e-4b6e-9a57-0d7b8f3c2a11")

    private fun entry(offset: ZoneOffset = ZoneOffset.ofHours(1), note: String? = null, origin: String? = null, deleted: Boolean = false) =
        IntakeEntry(
            id = id,
            instant = at("2026-11-15T07:05:00Z"),
            zoneOffset = offset,
            hydrationDay = day("2026-11-15"),
            volumeMl = 250,
            source = Source.MANUAL,
            hcDataOrigin = origin,
            note = note,
            createdAt = at("2026-11-15T07:05:02Z"),
            updatedAt = at("2026-11-15T07:05:02Z"),
            deletedAt = if (deleted) at("2026-11-15T09:00:00Z") else null,
        )

    private fun entriesCsv(vararg e: IntakeEntry, labels: Map<String, String> = emptyMap()) =
        StringBuilder().also { CsvExport.entries(e.toList(), labels, it) }.toString()

    @Test
    fun `untrusted text cannot become spreadsheet formulas`() {
        for (text in listOf("=1+1", "+SUM(1,2)", "-1+2", "@SUM(1,2)", "\t=1", "\r=1", "\n=1", "  =1")) {
            val csv = entriesCsv(entry(note = text, origin = "external"), labels = mapOf("external" to text))
            val escaped = "\"'" + text.replace("\"", "\"\"") + "\""
            assertThat(csv).contains(",$escaped,$escaped,")
        }
        assertThat(entriesCsv(entry(note = "after run"))).contains(",after run,")
    }

    @Test
    fun `entries csv has header crlf and the documented columns`() {
        val csv = entriesCsv(entry(note = "after run", origin = "com.fitbit.FitbitMobile"))
        val lines = csv.split("\r\n")
        assertThat(lines[0]).isEqualTo(
            "id,timestamp_utc,local_datetime,utc_offset,hydration_day,volume_ml,source,source_app,note,created_at_utc,updated_at_utc",
        )
        assertThat(lines[1]).isEqualTo(
            "5f0c1a52-8f2e-4b6e-9a57-0d7b8f3c2a11,2026-11-15T07:05:00Z,2026-11-15T08:05:00,+01:00,2026-11-15,250,MANUAL," +
                "com.fitbit.FitbitMobile,after run,2026-11-15T07:05:02Z,2026-11-15T07:05:02Z",
        )
        assertThat(lines[2]).isEmpty()
        assertThat(csv).doesNotContain("\n\n")
        assertThat(csv.replace("\r\n", "")).doesNotContain("\n")
        assertThat(csv.startsWith('﻿')).isFalse()
    }

    @Test
    fun `fields are quoted when needed and quotes doubled`() {
        val csv = entriesCsv(entry(note = "a,b \"c\"\nline2"))
        assertThat(csv).contains(",\"a,b \"\"c\"\"\nline2\",")
        assertThat(entriesCsv(entry(note = "plain"))).doesNotContain("\"")
        assertThat(entriesCsv(entry(note = "cr\rhere"))).contains("\"cr\rhere\"")
    }

    @Test
    fun `utc offsets are formatted with sign and zero as plus zero`() {
        assertThat(entriesCsv(entry(ZoneOffset.UTC))).contains("2026-11-15T07:05:00,+00:00,")
        assertThat(entriesCsv(entry(ZoneOffset.ofHoursMinutes(-3, -30)))).contains("2026-11-15T03:35:00,-03:30,")
        assertThat(entriesCsv(entry(ZoneOffset.ofHoursMinutes(5, 45)))).contains("2026-11-15T12:50:00,+05:45,")
    }

    @Test
    fun `tombstones are skipped and app labels are used`() {
        val csv = entriesCsv(entry(deleted = true), entry(origin = "a.b"), labels = mapOf("a.b" to "My App"))
        assertThat(csv.split("\r\n")).hasSize(3)
        assertThat(csv).contains(",My App,")
    }

    private fun goal(date: String, target: Int, calculated: Int?, mode: GoalMode = GoalMode.CALCULATED) = DailyGoal(
        day(date),
        target,
        calculated,
        mode,
        GoalBreakdown(1, 30, emptyList(), 0, 0, 0, ClampFlag.NONE),
        0,
        1,
        at("2026-11-01T00:00:00Z"),
    )

    @Test
    fun `daily csv includes no log days and rounds half up`() {
        val days = listOf(
            CsvDay(day("2026-11-12"), 0, 0),
            CsvDay(day("2026-11-13"), 2300, 3),
            CsvDay(day("2026-11-14"), 2150, 7),
            CsvDay(day("2026-11-15"), 1000, 2),
        )
        val goals = mapOf(
            day("2026-11-12") to goal("2026-11-12", 2000, 2000),
            day("2026-11-13") to goal("2026-11-13", 2300, 2300),
            day("2026-11-14") to goal("2026-11-14", 2300, 2300),
        )
        val totals = mapOf(
            day("2026-11-13") to DailyTotal(day("2026-11-13"), 2300, 3, at("2026-11-13T08:00:00Z"), at("2026-11-13T20:00:00Z")),
            day("2026-11-14") to DailyTotal(day("2026-11-14"), 2150, 7, at("2026-11-14T07:12:00Z"), at("2026-11-14T21:00:00Z")),
            day("2026-11-15") to DailyTotal(day("2026-11-15"), 1000, 2, at("2026-11-15T09:00:00Z"), at("2026-11-15T10:00:00Z")),
        )
        val out = StringBuilder()
        CsvExport.dailyTotals(days, goals, totals, out, ZoneId.of("UTC"))
        val lines = out.toString().split("\r\n")
        assertThat(lines[0]).isEqualTo(
            "date,total_ml,goal_ml,calculated_goal_ml,goal_mode,percent_of_goal,goal_met,entry_count,first_entry_local,last_entry_local",
        )
        assertThat(lines[1]).isEqualTo("2026-11-12,0,2000,2000,CALCULATED,0.0,false,0,,")
        assertThat(lines[2]).isEqualTo("2026-11-13,2300,2300,2300,CALCULATED,100.0,true,3,2026-11-13T08:00:00,2026-11-13T20:00:00")
        assertThat(lines[3]).isEqualTo("2026-11-14,2150,2300,2300,CALCULATED,93.5,false,7,2026-11-14T07:12:00,2026-11-14T21:00:00")
        assertThat(lines[4]).isEqualTo("2026-11-15,1000,,,,,,2,2026-11-15T09:00:00,2026-11-15T10:00:00")
    }

    @Test
    fun `daily percent rounds half up and uses a dot`() {
        val days = listOf(CsvDay(day("2026-11-12"), 1, 1))
        val goals = mapOf(day("2026-11-12") to goal("2026-11-12", 8, null, GoalMode.GLOBAL_OVERRIDE))
        val out = StringBuilder()
        java.util.Locale.setDefault(java.util.Locale.GERMANY)
        CsvExport.dailyTotals(days, goals, emptyMap(), out, ZoneId.of("UTC"))
        assertThat(out.toString()).contains("2026-11-12,1,8,,GLOBAL_OVERRIDE,12.5,false,1,,")
    }
}
