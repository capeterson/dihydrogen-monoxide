package casa.peterson.dhmo.core.backup

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class RetentionTest {
    private fun spec(): List<BackupFileInfo> {
        val skipFrom = LocalDate.parse("2026-10-20")
        val skipTo = LocalDate.parse("2026-11-05")
        val files = generateSequence(LocalDate.parse("2026-08-01")) { it.plusDays(1) }
            .takeWhile { it <= LocalDate.parse("2026-11-15") }
            .filter { it < skipFrom || it > skipTo }
            .map { fileInfo("${it}T03:00:00Z") }
            .toList()
        return files + fileInfo("2026-11-15T18:00:00Z")
    }

    @Test
    fun `the plan example keeps exactly twelve files`() {
        val files = spec()
        assertThat(files).hasSize(91)
        val plan = Retention.select(files)
        val expected = listOf(
            "2026-11-15T18:00:00Z", "2026-11-14T03:00:00Z", "2026-11-13T03:00:00Z", "2026-11-12T03:00:00Z",
            "2026-11-11T03:00:00Z", "2026-11-10T03:00:00Z", "2026-11-09T03:00:00Z", "2026-11-08T03:00:00Z",
            "2026-10-19T03:00:00Z", "2026-10-18T03:00:00Z", "2026-09-30T03:00:00Z", "2026-08-31T03:00:00Z",
        )
        assertThat(plan.keep.map { it.createdAt.toString() }).containsExactlyElementsIn(expected)
        assertThat(plan.keep).hasSize(12)
        assertThat(plan.delete).hasSize(79)
        assertThat(plan.delete.map { it.createdAt.toString() }).contains("2026-11-15T03:00:00Z")
    }

    @Test
    fun `empty list gives an empty plan`() {
        assertThat(Retention.select(emptyList())).isEqualTo(RetentionPlan(emptyList(), emptyList()))
    }

    @Test
    fun `a single file is kept`() {
        val only = fileInfo("2026-11-15T03:00:00Z")
        assertThat(Retention.select(listOf(only))).isEqualTo(RetentionPlan(listOf(only), emptyList()))
    }

    @Test
    fun `newer schema files and foreign names are always kept and never counted`() {
        val newer = (1..10).map { fileInfo("2026-11-0${it % 9 + 1}T04:00:00Z", schemaVersion = 2) }
        val foreign = fileInfo("2026-01-01T00:00:00Z", name = "notes.txt")
        val old = fileInfo("2026-11-15T03:00:00Z")
        val older = fileInfo("2026-11-14T03:00:00Z")
        val plan = Retention.select(newer + foreign + listOf(old, older), keepDaily = 1, keepWeekly = 1, keepMonthly = 1)
        assertThat(plan.keep).containsAtLeastElementsIn(newer + foreign)
        assertThat(plan.keep).contains(old)
        assertThat(plan.delete).containsExactly(older)
    }

    @Test
    fun `encrypted names are managed`() {
        val a = fileInfo("2026-11-15T03:00:00Z", name = "hydration-backup-v1-20261115T030000Z.json.gz.enc")
        val b = fileInfo("2026-11-15T02:00:00Z", name = "hydration-backup-v1-20261115T020000Z.json.gz.enc")
        assertThat(Retention.select(listOf(a, b), 1, 1, 1).delete).containsExactly(b)
    }

    @Test
    fun `iso week boundaries use the week based year`() {
        val sunday = fileInfo("2026-01-04T10:00:00Z") // 2026-W01
        val lastOf2025 = fileInfo("2025-12-29T10:00:00Z") // Monday, also 2026-W01
        val plan = Retention.select(listOf(sunday, lastOf2025), keepDaily = 0, keepWeekly = 4, keepMonthly = 0)
        assertThat(plan.keep).containsExactly(sunday)
    }
}
