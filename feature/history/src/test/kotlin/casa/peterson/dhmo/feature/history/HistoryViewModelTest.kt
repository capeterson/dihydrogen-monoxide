package casa.peterson.dhmo.feature.history

import android.content.Context
import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.data.time.DayTicker
import casa.peterson.dhmo.core.data.usecase.intake.DeleteIntakeUseCase
import casa.peterson.dhmo.core.data.usecase.intake.UndoDeleteIntakeUseCase
import casa.peterson.dhmo.core.model.Source
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class HistoryViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = MutableClock(Instant.parse("2026-04-01T12:00:00Z"))
    private val zone = FixedZoneProvider(ZoneOffset.UTC)
    private val settings = FakeSettingsRepository()
    private val intake = FakeIntakeRepository(clock, zone)
    private val ticker = DayTicker(context, clock, zone, settings)
    private val viewModel = HistoryViewModel(
        intake,
        FakeGoalRepository(clock),
        settings,
        ticker,
        DeleteIntakeUseCase(intake),
        UndoDeleteIntakeUseCase(intake),
        context,
    )

    @Test
    fun groupsEntriesByHydrationDayInDescendingOrder() = runTest {
        intake.put(TestData.entry(Instant.parse("2026-03-03T12:00:00Z")))
        intake.put(TestData.entry(Instant.parse("2026-03-03T08:00:00Z")))
        intake.put(TestData.entry(Instant.parse("2026-03-02T12:00:00Z")))
        val state = viewModel.uiState.first { it.totals.size == 2 }
        val sorted = intake.all.sortedByDescending { it.instant }
        val items = flowOf(groupHistory(PagingData.from(sorted), state)).asSnapshot()
        assertThat(items.map { it::class.simpleName }).containsExactly("DayHeader", "Entry", "Entry", "DayHeader", "Entry").inOrder()
        assertThat((items[0] as HistoryItem.DayHeader).date).isEqualTo(LocalDate.of(2026, 3, 3))
        assertThat((items[3] as HistoryItem.DayHeader).date).isEqualTo(LocalDate.of(2026, 3, 2))
    }

    @Test
    fun groupingUsesHydrationDayOrderEvenWhenInstantsCrossAfterTravel() = runTest {
        val newerDay = LocalDate.of(2026, 3, 3)
        val olderDay = newerDay.minusDays(1)
        val newerBucketEarlierInstant = TestData.entry(Instant.parse("2026-03-03T00:00:00Z")).copy(hydrationDay = newerDay)
        val olderBucketLaterInstant = TestData.entry(Instant.parse("2026-03-03T01:00:00Z")).copy(hydrationDay = olderDay)
        val sameDay = TestData.entry(Instant.parse("2026-03-02T23:00:00Z")).copy(hydrationDay = newerDay)
        // IntakeRepository.pagedHistory must order by hydration day, then instant, both descending.
        val ordered = listOf(olderBucketLaterInstant, sameDay, newerBucketEarlierInstant)
            .sortedWith(compareByDescending<casa.peterson.dhmo.core.model.IntakeEntry> { it.hydrationDay }.thenByDescending { it.instant })
        val items = flowOf(groupHistory(PagingData.from(ordered), HistoryUiState())).asSnapshot()
        assertThat(items.map { it::class.simpleName }).containsExactly("DayHeader", "Entry", "Entry", "DayHeader", "Entry").inOrder()
        assertThat((items[0] as HistoryItem.DayHeader).date).isEqualTo(newerDay)
        assertThat((items[3] as HistoryItem.DayHeader).date).isEqualTo(olderDay)
    }

    @Test
    fun flagsAnomaliesUsingEarlierLoggedDaysAndExcludesToday() = runTest {
        TestData.datasetF.forEachIndexed { index, volume ->
            val date = LocalDate.of(2026, 3, 1).plusDays(index.toLong())
            intake.put(TestData.entry(date.atTime(12, 0).toInstant(ZoneOffset.UTC), volume))
        }
        intake.put(TestData.entry(Instant.parse("2026-04-01T08:00:00Z"), 9000))
        val state = viewModel.uiState.first { it.totals.size == TestData.datasetF.size + 1 }
        assertThat(state.anomalies).doesNotContain(LocalDate.of(2026, 4, 1))
        assertThat(state.anomalies).containsExactly(LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 11))
    }

    @Test
    fun adoptsReadOnlyImportAndCanDeleteAndUndoIt() = runTest {
        val entry = TestData.entry(Instant.parse("2026-03-03T08:00:00Z"), source = Source.HEALTH_CONNECT_IMPORT)
        intake.put(entry)
        viewModel.adopt(entry.id)
        assertThat(intake.get(entry.id)?.hcAdopted).isTrue()
        viewModel.deleteEntry(entry.id)
        assertThat(intake.get(entry.id)?.deletedAt).isNotNull()
        viewModel.undoDelete(entry.id)
        assertThat(intake.get(entry.id)?.deletedAt).isNull()
    }
}
