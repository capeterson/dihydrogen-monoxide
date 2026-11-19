package casa.peterson.dhmo.feature.history

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import casa.peterson.dhmo.core.data.repository.GoalRepository
import casa.peterson.dhmo.core.data.repository.IntakeRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.time.DayTicker
import casa.peterson.dhmo.core.data.usecase.intake.DeleteIntakeUseCase
import casa.peterson.dhmo.core.data.usecase.intake.UndoDeleteIntakeUseCase
import casa.peterson.dhmo.core.domain.stats.DayStat
import casa.peterson.dhmo.core.domain.stats.anomalies
import casa.peterson.dhmo.core.model.DailyTotal
import casa.peterson.dhmo.core.model.IntakeEntry
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HistoryItem {
    data class DayHeader(val date: LocalDate, val totalMl: Int, val goalMl: Int?, val anomalous: Boolean, val entryCount: Int = 1) :
        HistoryItem
    data class Entry(val entry: IntakeEntry) : HistoryItem
}

data class HistoryUiState(
    val units: VolumeUnit = VolumeUnit.ML,
    val anomalies: Set<LocalDate> = emptySet(),
    val totals: Map<LocalDate, DailyTotal> = emptyMap(),
    val goals: Map<LocalDate, Int> = emptyMap(),
    val today: LocalDate? = null,
)

sealed interface HistoryEffect {
    data class Deleted(val id: UUID) : HistoryEffect
    data object Error : HistoryEffect
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val intake: IntakeRepository,
    goals: GoalRepository,
    settings: SettingsRepository,
    ticker: DayTicker,
    private val delete: DeleteIntakeUseCase,
    private val undo: UndoDeleteIntakeUseCase,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val uiState = combine(
        settings.settings,
        intake.observeDailyTotals(LocalDate.MIN, LocalDate.MAX),
        goals.observeGoals(LocalDate.MIN, LocalDate.MAX),
        ticker.today(),
    ) { current, totals, snapshots, today ->
        val goalMap = snapshots.associate { it.date to it.targetMl }
        val completed = totals.filter { it.date < today }.sortedBy { it.date }.map {
            DayStat(it.date, it.totalMl, goalMap[it.date], it.entryCount)
        }
        HistoryUiState(current.units, anomalies(completed).map { it.date }.toSet(), totals.associateBy { it.date }, goalMap, today)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), HistoryUiState())

    /**
     * The pager's output is cached first: `combine` re-emits the latest `PagingData` whenever [uiState] changes, and an
     * uncached `PagingData` can only be collected once (a second collection crashes). Caching it makes the repeat safe.
     */
    val entries: Flow<PagingData<HistoryItem>> = combine(intake.pagedHistory().cachedIn(viewModelScope), uiState) { paging, state ->
        groupHistory(paging, state)
    }.cachedIn(viewModelScope)

    private val effectChannel = Channel<HistoryEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    fun entriesForDay(day: LocalDate): Flow<List<IntakeEntry>> = intake.observeDay(day).map { it.sortedByDescending { row -> row.instant } }

    fun deleteEntry(id: UUID) = act {
        delete(id)
        effectChannel.send(HistoryEffect.Deleted(id))
    }

    fun undoDelete(id: UUID) = act { undo(id) }

    fun adopt(id: UUID) = act { intake.adopt(id) }

    fun sourceLabel(origin: String?): String? {
        if (origin == null) return null
        return try {
            val info = context.packageManager.getApplicationInfo(origin, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            origin
        }
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (_: IllegalArgumentException) {
                effectChannel.send(HistoryEffect.Error)
            } catch (_: IllegalStateException) {
                effectChannel.send(HistoryEffect.Error)
            } catch (_: NoSuchElementException) {
                effectChannel.send(HistoryEffect.Error)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}

internal fun groupHistory(paging: PagingData<IntakeEntry>, state: HistoryUiState): PagingData<HistoryItem> =
    paging.map<IntakeEntry, HistoryItem> { HistoryItem.Entry(it) }.insertSeparators { before, after ->
        val next = (after as? HistoryItem.Entry)?.entry ?: return@insertSeparators null
        val previous = (before as? HistoryItem.Entry)?.entry
        if (previous?.hydrationDay == next.hydrationDay) return@insertSeparators null
        val day = next.hydrationDay
        HistoryItem.DayHeader(day, state.totals[day]?.totalMl ?: 0, state.goals[day], day in state.anomalies)
    }
