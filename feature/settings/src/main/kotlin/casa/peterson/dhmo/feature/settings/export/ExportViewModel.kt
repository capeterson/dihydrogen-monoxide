package casa.peterson.dhmo.feature.settings.export

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.backup.BackupCodec
import casa.peterson.dhmo.core.backup.CsvDay
import casa.peterson.dhmo.core.backup.CsvExport
import casa.peterson.dhmo.core.common.DhmoDispatcher
import casa.peterson.dhmo.core.common.Dispatcher
import casa.peterson.dhmo.core.common.ZoneProvider
import casa.peterson.dhmo.core.common.suspendRunCatching
import casa.peterson.dhmo.core.data.repository.BackupRepository
import casa.peterson.dhmo.core.data.repository.GoalRepository
import casa.peterson.dhmo.core.data.repository.IntakeRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.domain.stats.buildDayStats
import casa.peterson.dhmo.core.domain.time.hydrationDay
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.OutputStreamWriter
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ExportKind(val mimeType: String) {
    ENTRIES_CSV("text/csv"),
    DAILY_CSV("text/csv"),
    JSON("application/json"),
}

/** [message] is a one-shot result shown in a snackbar; the screen calls [ExportViewModel.messageShown] after it. */
data class ExportUiState(val running: ExportKind? = null, val message: ExportMessage? = null)

sealed interface ExportMessage {
    data class Saved(val kind: ExportKind) : ExportMessage

    data object Failed : ExportMessage
}

/** CSV (entries, daily totals) and JSON export through the Storage Access Framework (TECHNICAL_PLAN §11.3, §11.10). */
@HiltViewModel
internal class ExportViewModel
@Inject
constructor(
    private val intake: IntakeRepository,
    private val goals: GoalRepository,
    private val backup: BackupRepository,
    private val settings: SettingsRepository,
    private val files: DocumentFiles,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
    @param:Dispatcher(DhmoDispatcher.IO) private val io: CoroutineDispatcher,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    /** The name offered to `ACTION_CREATE_DOCUMENT`, in local time. */
    fun suggestedName(kind: ExportKind): String {
        val local = clock.instant().atZone(zoneProvider.current())
        return when (kind) {
            ExportKind.ENTRIES_CSV -> "dihydrogen-monoxide-entries-${DATE.format(local)}.csv"
            ExportKind.DAILY_CSV -> "dihydrogen-monoxide-daily-${DATE.format(local)}.csv"
            ExportKind.JSON -> "dihydrogen-monoxide-export-${DATE_TIME.format(local)}.json"
        }
    }

    /** Writes [kind] to [uri]; a null [uri] means the user cancelled the picker. */
    fun export(kind: ExportKind, uri: Uri?) {
        if (uri == null || _uiState.value.running != null) return
        _uiState.update { it.copy(running = kind) }
        viewModelScope.launch {
            val result = suspendRunCatching { withContext(io) { write(kind, uri) } }
            result.onFailure { Log.w(TAG, "Export failed", it) }
            _uiState.update {
                ExportUiState(running = null, message = if (result.isSuccess) ExportMessage.Saved(kind) else ExportMessage.Failed)
            }
        }
    }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    private suspend fun write(kind: ExportKind, uri: Uri) {
        when (kind) {
            ExportKind.JSON -> {
                val bytes = BackupCodec.encodeJson(backup.exportDocument(), pretty = true)
                files.openOutput(uri).use { it.write(bytes) }
            }

            ExportKind.ENTRIES_CSV -> {
                val today = today()
                val first = intake.firstDay()
                val entries = if (first == null) emptyList() else intake.entries(first, today)
                val labels = entries.mapNotNull {
                    it.hcDataOrigin
                }.distinct().mapNotNull { pkg -> files.appLabel(pkg)?.let { pkg to it } }.toMap()
                writeText(uri) { CsvExport.entries(entries, labels, it) }
            }

            ExportKind.DAILY_CSV -> {
                // One row per day from the first logged day to yesterday, including no-log days (§11.10).
                val yesterday = today().minusDays(1)
                val first = intake.firstDay()
                val totals = if (first == null || first > yesterday) emptyList() else intake.dailyTotals(first, yesterday)
                val goalRows = if (first == null || first > yesterday) emptyList() else goals.goals(first, yesterday)
                val goalMap = goalRows.associateBy { it.date }
                val days = if (first == null) {
                    emptyList()
                } else {
                    buildDayStats(first, yesterday, totals, goalMap.mapValues { it.value.targetMl })
                        .map { CsvDay(it.date, it.totalMl, it.entryCount) }
                }
                writeText(uri) { CsvExport.dailyTotals(days, goalMap, totals.associateBy { it.date }, it, zoneProvider.current()) }
            }
        }
    }

    private fun writeText(uri: Uri, block: (Appendable) -> Unit) {
        val text = StringBuilder().also(block).toString()
        files.openOutput(uri).use { out -> OutputStreamWriter(out, Charsets.UTF_8).use { it.write(text) } }
    }

    private suspend fun today(): LocalDate {
        val now = clock.instant()
        val dayStart = LocalTime.ofSecondOfDay(settings.current().dayStartMinutes * SECONDS_PER_MINUTE)
        return hydrationDay(now, zoneProvider.current().rules.getOffset(now), dayStart)
    }

    private companion object {
        const val TAG = "ExportViewModel"
        const val SECONDS_PER_MINUTE = 60L
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
