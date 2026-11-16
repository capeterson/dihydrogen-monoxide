package casa.peterson.dhmo.feature.insights

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.common.DhmoDispatcher
import casa.peterson.dhmo.core.common.Dispatcher
import casa.peterson.dhmo.core.common.ZoneProvider
import casa.peterson.dhmo.core.data.repository.IntakeRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.time.DayTicker
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.feature.insights.domain.LoadStatsRangeUseCase
import casa.peterson.dhmo.feature.insights.domain.ObserveInsightsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Day, Week, Month, Year and Custom statistics (§13.7). Results are cached per input in a small LRU map, so switching
 * back to a range whose data has not changed is instant; computation runs on the Default dispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val ticker: DayTicker,
    private val settings: SettingsRepository,
    private val intake: IntakeRepository,
    private val loadStats: LoadStatsRangeUseCase,
    observeInsights: ObserveInsightsUseCase,
    private val zoneProvider: ZoneProvider,
    @Dispatcher(DhmoDispatcher.Default) private val compute: CoroutineDispatcher,
) : ViewModel() {
    /** Null until today is known: the initial range is anchored on it. */
    private val selection = MutableStateFlow<RangeSelection?>(initialSelection(savedStateHandle))
    private val cache = object : LinkedHashMap<InsightsInputs, InsightsUiState.Ready>(CACHE_SIZE, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<InsightsInputs, InsightsUiState.Ready>?) = size > CACHE_SIZE
    }

    /** How many computations ran (cache misses); exposed for tests. */
    internal var computations = 0
        private set

    private val anchor = savedStateHandle.get<String>(ARG_RANGE)?.let { name -> InsightsRange.entries.firstOrNull { it.name == name } }
        ?: InsightsRange.WEEK
    private val anchorDay = savedStateHandle.get<Long>(ARG_ANCHOR)?.let(::safeEpochDay)

    val uiState: StateFlow<InsightsUiState> =
        combine(ticker.today(), settings.settings, observeInsights().onStart { emit(emptyList()) }) { today, current, insights ->
            Triple(today, current, insights)
        }.flatMapLatest { (today, current, insights) ->
            val firstDay = intake.firstDay()
            if (firstDay == null) return@flatMapLatest flowOf<InsightsUiState>(InsightsUiState.Empty)
            val chosen = selection.value ?: RangeSelection.forRange(anchor, minOf(anchorDay ?: today, today)).also { selection.value = it }
            selection.flatMapLatest { selected -> load(selected ?: chosen, today, current, firstDay) }
                .mapLatest { inputs ->
                    cache[inputs] ?: withContext(compute) { computeInsights(inputs, insights) }.also {
                        computations++
                        cache[inputs] = it
                    }
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState.Loading)

    fun onRangeSelected(range: InsightsRange) {
        if (range == InsightsRange.CUSTOM) return // The custom tab asks for dates first; see onCustomRange.
        val ready = uiState.value as? InsightsUiState.Ready ?: return
        selection.value = RangeSelection.forRange(range, minOf(ready.selection.to, ready.today))
    }

    fun onPrevious() = selection.update { it?.shifted(forward = false) }

    fun onNext() = selection.update { current ->
        val today = (uiState.value as? InsightsUiState.Ready)?.today ?: return@update current
        if (current != null && current.hasNext(today)) current.shifted(forward = true) else current
    }

    fun onCustomRange(from: LocalDate, to: LocalDate, compare: Boolean) {
        require(!to.isBefore(from)) { "Range ends before it starts" }
        require(RangeSelection(InsightsRange.CUSTOM, from, to).lengthDays <= RangeSelection.MAX_CUSTOM_DAYS) { "Range too long" }
        selection.value = RangeSelection(InsightsRange.CUSTOM, from, to, compare)
    }

    private fun load(selected: RangeSelection, today: LocalDate, current: AppSettings, firstDay: LocalDate): Flow<InsightsInputs> {
        val chartTo = minOf(selected.to, today)
        // Rolling overlays need ROLLING_LONG − 1 days before the range; streaks need everything from the first entry.
        val statsFrom = minOf(firstDay, selected.from.minusDays(ROLLING_LONG - 1L))
        val (previousFrom, previousTo) = selected.previousPeriod()
        val previous = if (selected.compare) loadStats.observe(previousFrom, minOf(previousTo, today.minusDays(1))) else flowOf(emptyList())
        val entries = if (chartTo.isBefore(selected.from)) flowOf(emptyList()) else intake.observeRange(selected.from, chartTo)
        return combine(loadStats.observe(statsFrom, chartTo), entries, previous) { history, rangeEntries, previousDays ->
            InsightsInputs(
                selection = selected,
                today = today,
                settings = current,
                zone = zoneProvider.current(),
                firstDataDay = firstDay,
                statsFrom = statsFrom,
                history = history,
                entries = rangeEntries,
                previous = previousDays,
            )
        }
    }

    internal companion object {
        const val ARG_RANGE = "range"
        const val ARG_ANCHOR = "anchorEpochDay"
        const val ARG_FROM = "fromEpochDay"
        const val ARG_TO = "toEpochDay"
        const val ARG_COMPARE = "compare"
        private const val CACHE_SIZE = 8
        private const val LOAD_FACTOR = 0.75f

        /** A CustomRangeRoute's arguments select a custom range directly; anything invalid falls back to the default. */
        fun initialSelection(handle: SavedStateHandle): RangeSelection? {
            val from = handle.get<Long>(ARG_FROM)?.let(::safeEpochDay)
            val to = handle.get<Long>(ARG_TO)?.let(::safeEpochDay)
            return if (from == null || to == null || to.isBefore(from)) {
                null
            } else {
                RangeSelection(InsightsRange.CUSTOM, from, to, handle.get<Boolean>(ARG_COMPARE) ?: false)
                    .takeIf { it.lengthDays <= RangeSelection.MAX_CUSTOM_DAYS }
            }
        }

        /** Route arguments are untrusted (§13.3): an epoch day outside LocalDate's range is ignored. */
        fun safeEpochDay(epochDay: Long): LocalDate? = runCatching { LocalDate.ofEpochDay(epochDay) }.getOrNull()
    }
}
