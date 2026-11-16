package casa.peterson.dhmo.feature.insights

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.data.time.DayTicker
import casa.peterson.dhmo.core.domain.stats.TrendDirection
import casa.peterson.dhmo.core.testing.FakeGoalRepository
import casa.peterson.dhmo.core.testing.FakeIntakeRepository
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.FixedZoneProvider
import casa.peterson.dhmo.core.testing.MainDispatcherRule
import casa.peterson.dhmo.core.testing.MutableClock
import casa.peterson.dhmo.feature.insights.domain.LoadStatsRangeUseCase
import casa.peterson.dhmo.feature.insights.domain.ObserveInsightsUseCase
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class InsightsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    /** Monday 2026-03-16 at 10:00 UTC: dataset A is the two complete weeks before. */
    private val clock = MutableClock(Instant.parse("2026-03-16T10:00:00Z"))
    private val zone = FixedZoneProvider(ZoneOffset.UTC)
    private val settings = FakeSettingsRepository()
    private val intake = FakeIntakeRepository(clock, zone)
    private val goals = FakeGoalRepository(clock)
    private val ticker = DayTicker(ApplicationProvider.getApplicationContext<Context>(), clock, zone, settings)
    private val loadStats = LoadStatsRangeUseCase(intake, goals)

    private fun model(vararg args: Pair<String, Any?>) = InsightsViewModel(
        SavedStateHandle(args.toMap()),
        ticker,
        settings,
        intake,
        loadStats,
        ObserveInsightsUseCase(ticker, settings, loadStats, intake),
        zone,
        UnconfinedTestDispatcher(main.dispatcher.scheduler),
    )

    private suspend fun seedDatasetA(withToday: Int? = null) {
        DatasetA.entries.forEach(intake::put)
        withToday?.let { intake.put(DatasetA.entry(DatasetA.today, 8, it)) }
        DatasetA.saveGoals(goals)
    }

    /**
     * Runs what is due now and returns the Ready state. Never advances virtual time: DayTicker waits for the next day
     * boundary, so advancing would roll "today" over (and advanceUntilIdle would never return).
     */
    private fun TestScope.ready(vm: InsightsViewModel): InsightsUiState.Ready {
        runCurrent()
        return vm.uiState.value as InsightsUiState.Ready
    }

    @Test
    fun rangeArithmetic() {
        val wednesday = LocalDate.of(2026, 3, 11)
        val week = RangeSelection.forRange(InsightsRange.WEEK, wednesday)
        assertThat(week.from).isEqualTo(LocalDate.of(2026, 3, 9))
        assertThat(week.to).isEqualTo(LocalDate.of(2026, 3, 15))
        assertThat(week.shifted(forward = false).from).isEqualTo(LocalDate.of(2026, 3, 2))
        val month = RangeSelection.forRange(InsightsRange.MONTH, LocalDate.of(2024, 2, 10))
        assertThat(month.to).isEqualTo(LocalDate.of(2024, 2, 29))
        assertThat(month.shifted(forward = true).to).isEqualTo(LocalDate.of(2024, 3, 31))
        assertThat(RangeSelection.forRange(InsightsRange.YEAR, wednesday).lengthDays).isEqualTo(365)
        val custom = RangeSelection(InsightsRange.CUSTOM, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 15), compare = true)
        assertThat(custom.previousPeriod()).isEqualTo(LocalDate.of(2026, 2, 16) to LocalDate.of(2026, 3, 1))
        assertThat(custom.shifted(forward = false).to).isEqualTo(LocalDate.of(2026, 3, 1))
        assertThat(week.hasNext(LocalDate.of(2026, 3, 16))).isTrue()
        assertThat(RangeSelection.forRange(InsightsRange.WEEK, LocalDate.of(2026, 3, 16)).hasNext(LocalDate.of(2026, 3, 16))).isFalse()
    }

    @Test
    fun emptyWithoutEntries() = runTest {
        val vm = model()
        vm.uiState.launchIn(backgroundScope)
        runCurrent()
        assertThat(vm.uiState.value).isEqualTo(InsightsUiState.Empty)
    }

    @Test
    fun defaultsToTheCurrentWeekAndExcludesToday() = runTest {
        seedDatasetA(withToday = 300)
        val vm = model()
        vm.uiState.launchIn(backgroundScope)
        val state = ready(vm)
        assertThat(state.selection).isEqualTo(RangeSelection.forRange(InsightsRange.WEEK, DatasetA.today))
        // Only Monday the 16th (today) is in range so far: it is shown but excluded from the statistics.
        assertThat(state.days.map { it.date }).containsExactly(DatasetA.today)
        assertThat(state.days.single().totalMl).isEqualTo(300)
        assertThat(state.summary).isNull()
        assertThat(state.attainment.dayCount).isEqualTo(0)
        assertThat(state.hasNext).isFalse()
        assertThat(state.hasPrevious).isTrue()
    }

    @Test
    fun customRangeOverDatasetAMatchesTheGoldenValues() = runTest {
        seedDatasetA()
        val vm = model("fromEpochDay" to DatasetA.first.toEpochDay(), "toEpochDay" to DatasetA.last.toEpochDay())
        vm.uiState.launchIn(backgroundScope)
        val state = ready(vm)
        assertThat(state.selection.range).isEqualTo(InsightsRange.CUSTOM)
        val summary = checkNotNull(state.summary)
        assertThat(summary.dayCount).isEqualTo(13)
        assertThat(summary.meanMl).isWithin(1e-3).of(1923.0769)
        assertThat(state.attainment.daysMet).isEqualTo(8)
        assertThat(state.consistency).isEqualTo(76)
        assertThat(state.trend.direction).isEqualTo(TrendDirection.STABLE)
        assertThat(state.dayOfWeek.weakest).isEqualTo(java.time.DayOfWeek.SATURDAY)
        assertThat(state.goalStreak.longest).isEqualTo(2)
        // Rolling means are aligned with the visible days and use earlier days when there are any (none here).
        assertThat(state.rolling7).hasSize(14)
        assertThat(state.rolling7[6]).isWithin(1e-3).of(1925.0)
        assertThat(state.timeOfDay?.eveningShare).isNotNull()
    }

    @Test
    fun navigationMovesByWholePeriodsAndStopsAtToday() = runTest {
        seedDatasetA()
        val vm = model()
        vm.uiState.launchIn(backgroundScope)
        ready(vm)
        vm.onNext()
        runCurrent()
        assertThat(ready(vm).selection.from).isEqualTo(LocalDate.of(2026, 3, 16))
        vm.onPrevious()
        runCurrent()
        val previous = ready(vm)
        assertThat(previous.selection.from).isEqualTo(LocalDate.of(2026, 3, 9))
        assertThat(previous.days).hasSize(7)
        assertThat(previous.attainment.daysMet).isEqualTo(5)
        vm.onRangeSelected(InsightsRange.MONTH)
        runCurrent()
        assertThat(ready(vm).selection).isEqualTo(RangeSelection.forRange(InsightsRange.MONTH, LocalDate.of(2026, 3, 15)))
    }

    @Test
    fun dayViewHasHourlyBarsAndAnExpectedLine() = runTest {
        seedDatasetA()
        val vm = model("range" to "DAY", "anchorEpochDay" to DatasetA.last.toEpochDay())
        vm.uiState.launchIn(backgroundScope)
        val state = ready(vm)
        val hourly = checkNotNull(state.hourly)
        assertThat(hourly).hasSize(24)
        assertThat(hourly.last().cumulativeMl).isEqualTo(2400)
        assertThat(hourly[8].volumeMl).isEqualTo(800)
        val expected = checkNotNull(state.expectedLine)
        assertThat(expected).hasSize(24)
        assertThat(expected.last()).isEqualTo(2000f)
        assertThat(expected[6]).isEqualTo(0f) // before the 07:00 wake time
    }

    @Test
    fun cachedRangesAreNotRecomputed() = runTest {
        seedDatasetA()
        val vm = model()
        vm.uiState.launchIn(backgroundScope)
        ready(vm)
        vm.onRangeSelected(InsightsRange.MONTH)
        runCurrent()
        val afterMonth = vm.computations
        vm.onRangeSelected(InsightsRange.WEEK)
        runCurrent()
        vm.onRangeSelected(InsightsRange.MONTH)
        runCurrent()
        assertThat(vm.computations).isEqualTo(afterMonth)
    }

    @Test
    fun newEntriesRecompute() = runTest {
        seedDatasetA()
        val vm = model("fromEpochDay" to DatasetA.first.toEpochDay(), "toEpochDay" to DatasetA.last.toEpochDay())
        vm.uiState.launchIn(backgroundScope)
        ready(vm)
        intake.put(DatasetA.entry(LocalDate.of(2026, 3, 4), 9, 2000))
        runCurrent()
        assertThat(ready(vm).summary!!.dayCount).isEqualTo(14)
    }

    @Test
    fun comparisonUsesThePreviousEqualPeriod() = runTest {
        seedDatasetA()
        val vm = model()
        vm.uiState.launchIn(backgroundScope)
        ready(vm)
        vm.onCustomRange(LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 15), compare = true)
        runCurrent()
        val state = ready(vm)
        assertThat(state.comparison!!.meanMl).isWithin(1e-3).of((2100 + 1800 + 2500 + 1950 + 2000 + 1200) / 6.0)
        assertThat(state.summary!!.meanMl).isWithin(1e-3).of((2200 + 2300 + 1600 + 2000 + 2050 + 900 + 2400) / 7.0)
    }

    @Test
    fun untrustedRouteArgumentsFallBack() {
        assertThat(InsightsViewModel.safeEpochDay(Long.MAX_VALUE)).isNull()
        assertThat(InsightsViewModel.initialSelection(SavedStateHandle(mapOf("fromEpochDay" to 10L, "toEpochDay" to 5L)))).isNull()
        assertThat(InsightsViewModel.initialSelection(SavedStateHandle(mapOf("fromEpochDay" to 0L, "toEpochDay" to 400L)))).isNull()
        assertThat(runCatching { model().onCustomRange(LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 1), false) }.isFailure).isTrue()
    }
}
