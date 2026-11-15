package casa.peterson.dhmo.core.backup

import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BackupMergeTest {
    private val id = "5f0c1a52-8f2e-4b6e-9a57-0d7b8f3c2a11"
    private val other = "6f0c1a52-8f2e-4b6e-9a57-0d7b8f3c2a12"

    private fun entries(vararg e: IntakeEntryDto) = BackupEntities(intakeEntries = e.toList())

    private fun mergedEntries(local: IntakeEntryDto, incoming: IntakeEntryDto) =
        BackupMerge.merge(entries(local), entries(incoming)).intakeEntries.single()

    @Test
    fun `entries are unioned by id`() {
        val merged = BackupMerge.merge(
            entries(entryDto(id, "2026-11-15T10:00:00Z")),
            entries(entryDto(other, "2026-11-15T10:00:00Z")),
        )
        assertThat(merged.intakeEntries.map { it.id }).containsExactly(id, other).inOrder()
    }

    @Test
    fun `later update wins as a whole`() {
        val local = entryDto(id, "2026-11-15T10:00:00Z", volumeMl = 250)
        val incoming = entryDto(id, "2026-11-15T11:00:00Z", volumeMl = 300)
        assertThat(mergedEntries(local, incoming)).isEqualTo(incoming)
        assertThat(mergedEntries(incoming, local)).isEqualTo(incoming)
    }

    @Test
    fun `a deletion newer than an edit wins`() {
        val edit = entryDto(id, "2026-11-15T10:00:00Z", volumeMl = 300)
        val deletion = entryDto(id, "2026-11-15T11:00:00Z", deletedAt = "2026-11-15T11:00:00Z")
        assertThat(mergedEntries(local = edit, incoming = deletion)).isEqualTo(deletion)
        assertThat(mergedEntries(local = deletion, incoming = edit)).isEqualTo(deletion)
    }

    @Test
    fun `an edit newer than a deletion resurrects the entry`() {
        val deletion = entryDto(id, "2026-11-15T10:00:00Z", deletedAt = "2026-11-15T10:00:00Z")
        val edit = entryDto(id, "2026-11-15T11:00:00Z", volumeMl = 300)
        assertThat(mergedEntries(local = deletion, incoming = edit)).isEqualTo(edit)
        assertThat(mergedEntries(local = edit, incoming = deletion).deletedAt).isNull()
    }

    @Test
    fun `a tie keeps local`() {
        val local = entryDto(id, "2026-11-15T10:00:00Z", volumeMl = 250)
        val incoming = entryDto(id, "2026-11-15T10:00:00Z", volumeMl = 999, deletedAt = "2026-11-15T10:00:00Z")
        assertThat(mergedEntries(local, incoming)).isEqualTo(local)
    }

    @Test
    fun `containers and day overrides follow the same rule`() {
        val c1 = ContainerDto("c", "Old", 250, "GLASS", 0, at("2026-11-01T00:00:00Z"))
        val c2 = c1.copy(name = "New", updatedAt = at("2026-11-02T00:00:00Z"))
        val c3 = c1.copy(name = "Tie")
        val o1 = DayOverrideDto(day("2026-11-10"), 3000, null, at("2026-11-10T00:00:00Z"))
        val o2 = o1.copy(targetMl = 100, updatedAt = at("2026-11-11T00:00:00Z"), deletedAt = at("2026-11-11T00:00:00Z"))
        val merged = BackupMerge.merge(
            BackupEntities(containers = listOf(c1), dayOverrides = listOf(o1)),
            BackupEntities(containers = listOf(c2), dayOverrides = listOf(o2)),
        )
        assertThat(merged.containers).containsExactly(c2)
        assertThat(merged.dayOverrides).containsExactly(o2)
        assertThat(BackupMerge.merge(BackupEntities(containers = listOf(c1)), BackupEntities(containers = listOf(c3))).containers)
            .containsExactly(c1)
    }

    @Test
    fun `profile snapshots keep local on an id clash`() {
        val local = fullEntities().profileSnapshots[0]
        val clash = local.copy(weightKg = 99.0, createdAt = at("2027-01-01T00:00:00Z"))
        val extra = fullEntities().profileSnapshots[1]
        val merged = BackupMerge.merge(
            BackupEntities(profileSnapshots = listOf(local)),
            BackupEntities(profileSnapshots = listOf(clash, extra)),
        )
        assertThat(merged.profileSnapshots).containsExactly(local, extra).inOrder()
    }

    @Test
    fun `daily goals weather health and activity pick the later stamp`() {
        val full = fullEntities()
        val goal = full.dailyGoals[0]
        val weather = full.weatherDays[0]
        val health = full.healthDaily[0]
        val activity = full.activityAdjustments[0]
        val newer = BackupEntities(
            dailyGoals = listOf(goal.copy(targetMl = 1, updatedAt = goal.updatedAt.plusSeconds(1))),
            weatherDays = listOf(weather.copy(extraMl = 1, fetchedAt = weather.fetchedAt.plusSeconds(1))),
            healthDaily = listOf(health.copy(steps = 1, updatedAt = health.updatedAt.plusSeconds(1))),
            activityAdjustments = listOf(activity.copy(minutes = 1, updatedAt = activity.updatedAt.plusSeconds(1))),
        )
        val tie = BackupEntities(
            dailyGoals = listOf(goal.copy(targetMl = 1)),
            weatherDays = listOf(weather.copy(extraMl = 1)),
            healthDaily = listOf(health.copy(steps = 1)),
            activityAdjustments = listOf(activity.copy(minutes = 1)),
        )
        val base = BackupEntities(
            dailyGoals = listOf(goal),
            weatherDays = listOf(weather),
            healthDaily = listOf(health),
            activityAdjustments = listOf(activity),
        )
        assertThat(BackupMerge.merge(base, newer)).isEqualTo(newer)
        assertThat(BackupMerge.merge(newer, base)).isEqualTo(newer)
        assertThat(BackupMerge.merge(base, tie)).isEqualTo(base)
    }

    @Test
    fun `activity adjustments are keyed by source and record id`() {
        val a = fullEntities().activityAdjustments[0]
        val sameIdOtherSource = a.copy(source = "MANUAL_PLANNED")
        val merged = BackupMerge.merge(
            BackupEntities(activityAdjustments = listOf(a)),
            BackupEntities(activityAdjustments = listOf(sameIdOtherSource)),
        )
        assertThat(merged.activityAdjustments).hasSize(2)
    }

    @Test
    fun `document merge keeps local settings and recomputes counts`() {
        val local = emptyDocument(entries(entryDto(id, "2026-11-15T10:00:00Z")), AppSettings(units = VolumeUnit.L))
        val incoming = emptyDocument(
            entries(entryDto(other, "2026-11-15T10:00:00Z", deletedAt = "2026-11-15T10:00:00Z")),
            AppSettings(units = VolumeUnit.IMP_FL_OZ),
        )
        val merged = BackupMerge.merge(local, incoming)
        assertThat(merged.settings.units).isEqualTo(VolumeUnit.L)
        assertThat(merged.counts.intakeEntries).isEqualTo(1)
        assertThat(merged.counts.intakeTombstones).isEqualTo(1)
    }

    @Test
    fun `merging with empty sides is the identity`() {
        val full = fullEntities()
        assertThat(BackupMerge.merge(full, BackupEntities())).isEqualTo(full)
        assertThat(BackupMerge.merge(BackupEntities(), full)).isEqualTo(full)
        assertThat(BackupMerge.merge(full, full)).isEqualTo(full)
    }
}
