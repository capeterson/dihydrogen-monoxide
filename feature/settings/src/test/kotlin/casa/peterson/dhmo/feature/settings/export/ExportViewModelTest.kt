package casa.peterson.dhmo.feature.settings.export

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.backup.BackupCodec
import casa.peterson.dhmo.core.backup.BackupCounts
import casa.peterson.dhmo.core.backup.BackupDocument
import casa.peterson.dhmo.core.backup.BackupEntities
import casa.peterson.dhmo.core.model.ClampFlag
import casa.peterson.dhmo.core.model.DailyGoal
import casa.peterson.dhmo.core.model.GoalBreakdown
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.testing.FakeBackupRepository
import casa.peterson.dhmo.core.testing.FakeGoalRepository
import casa.peterson.dhmo.core.testing.FakeIntakeRepository
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.FixedZoneProvider
import casa.peterson.dhmo.core.testing.MainDispatcherRule
import casa.peterson.dhmo.core.testing.MutableClock
import casa.peterson.dhmo.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    /** 2026-11-15 09:30 UTC = 10:30 in Berlin. */
    private val clock = MutableClock(Instant.parse("2026-11-15T09:30:00Z"))
    private val zone = FixedZoneProvider()
    private val intake = FakeIntakeRepository(clock, zone)
    private val goals = FakeGoalRepository(clock)
    private val doc = BackupDocument(
        appVersion = "1.0.0", appVersionCode = 1, dbVersion = 1, policyVersion = 1, createdAt = clock.instant(), deviceName = "Pixel",
        counts = BackupCounts(0, 0, 0, 0, 0, 0, 0, 0, 0), entities = BackupEntities(), settings = AppSettings(),
    )
    private val backup = FakeBackupRepository(doc)
    private val files = FakeDocumentFiles(labels = mapOf("com.example.water" to "Water app"))
    private val uri = Uri.parse("content://docs/out")

    private val viewModel by lazy {
        ExportViewModel(intake, goals, backup, FakeSettingsRepository(), files, clock, zone, main.dispatcher)
    }

    @Test
    fun `suggested names follow the documented patterns in local time`() {
        assertThat(viewModel.suggestedName(ExportKind.ENTRIES_CSV)).isEqualTo("dihydrogen-monoxide-entries-20261115.csv")
        assertThat(viewModel.suggestedName(ExportKind.DAILY_CSV)).isEqualTo("dihydrogen-monoxide-daily-20261115.csv")
        assertThat(viewModel.suggestedName(ExportKind.JSON)).isEqualTo("dihydrogen-monoxide-export-20261115-103000.json")
    }

    @Test
    fun `json export writes the backup document`() = runTest {
        viewModel.export(ExportKind.JSON, uri)

        assertThat(BackupCodec.decode(files.written.getValue(uri).toByteArray())).isEqualTo(doc)
        assertThat(viewModel.uiState.value).isEqualTo(ExportUiState(message = ExportMessage.Saved(ExportKind.JSON)))
        viewModel.messageShown()
        assertThat(viewModel.uiState.value.message).isNull()
    }

    @Test
    fun `entries csv uses app labels and skips tombstones`() = runTest {
        intake.put(TestData.entry(Instant.parse("2026-11-14T07:05:00Z"), 250, ZoneOffset.ofHours(1)).copy(note = "after run"))
        intake.put(TestData.importedEntry(Instant.parse("2026-11-14T08:00:00Z")))
        val deleted = TestData.entry(Instant.parse("2026-11-14T09:00:00Z")).copy(deletedAt = Instant.parse("2026-11-14T09:01:00Z"))
        intake.put(deleted)

        viewModel.export(ExportKind.ENTRIES_CSV, uri)

        val lines = files.text(uri).split("\r\n").filter { it.isNotEmpty() }
        assertThat(lines).hasSize(3)
        assertThat(lines[0]).startsWith("id,timestamp_utc,local_datetime,utc_offset")
        assertThat(lines[1]).contains(",2026-11-14T08:05:00,+01:00,2026-11-14,250,MANUAL,,after run,")
        assertThat(lines[2]).contains(",HEALTH_CONNECT_IMPORT,Water app,")
        assertThat(files.text(uri)).doesNotContain(deleted.id.toString())
    }

    @Test
    fun `daily csv runs from the first logged day to yesterday including no-log days`() = runTest {
        intake.put(TestData.entry(Instant.parse("2026-11-12T07:00:00Z"), 1000))
        intake.put(TestData.entry(Instant.parse("2026-11-12T15:00:00Z"), 1300))
        intake.put(TestData.entry(Instant.parse("2026-11-15T08:00:00Z"), 400)) // today: not exported
        goals.saveSnapshot(
            DailyGoal(
                LocalDate.of(2026, 11, 12),
                2000,
                2000,
                GoalMode.CALCULATED,
                GoalBreakdown(1, 30, emptyList(), 2000, 2000, 2000, ClampFlag.NONE),
                0,
                1,
                clock.instant(),
            ),
        )

        viewModel.export(ExportKind.DAILY_CSV, uri)

        val lines = files.text(uri).split("\r\n").filter { it.isNotEmpty() }
        assertThat(lines.map { it.substringBefore(',') }).containsExactly("date", "2026-11-12", "2026-11-13", "2026-11-14").inOrder()
        // Local times in Berlin (+01:00).
        assertThat(lines[1]).isEqualTo("2026-11-12,2300,2000,2000,CALCULATED,115.0,true,2,2026-11-12T08:00:00,2026-11-12T16:00:00")
        assertThat(lines[2]).isEqualTo("2026-11-13,0,,,,,,0,,")
    }

    @Test
    fun `an empty history exports only the header`() = runTest {
        viewModel.export(ExportKind.DAILY_CSV, uri)
        assertThat(files.text(uri).trim()).startsWith("date,total_ml")
        assertThat(files.text(uri).trim().lines()).hasSize(1)
    }

    @Test
    fun `cancelled picker does nothing and a write failure is reported`() = runTest {
        viewModel.export(ExportKind.JSON, null)
        assertThat(files.written).isEmpty()
        assertThat(viewModel.uiState.value).isEqualTo(ExportUiState())

        files.failWrites = true
        viewModel.export(ExportKind.ENTRIES_CSV, uri)
        assertThat(viewModel.uiState.value.message).isEqualTo(ExportMessage.Failed)
        assertThat(viewModel.uiState.value.running).isNull()
    }
}
