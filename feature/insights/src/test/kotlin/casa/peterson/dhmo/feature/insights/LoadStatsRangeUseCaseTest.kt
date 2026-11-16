package casa.peterson.dhmo.feature.insights

import app.cash.turbine.test
import casa.peterson.dhmo.core.domain.stats.DayStat
import casa.peterson.dhmo.core.testing.FakeGoalRepository
import casa.peterson.dhmo.core.testing.FakeIntakeRepository
import casa.peterson.dhmo.core.testing.FixedZoneProvider
import casa.peterson.dhmo.core.testing.MutableClock
import casa.peterson.dhmo.feature.insights.domain.LoadStatsRangeUseCase
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LoadStatsRangeUseCaseTest {
    private val clock = MutableClock(Instant.parse("2026-03-16T10:00:00Z"))
    private val intake = FakeIntakeRepository(clock, FixedZoneProvider(ZoneOffset.UTC))
    private val goals = FakeGoalRepository(clock)
    private val useCase = LoadStatsRangeUseCase(intake, goals)

    @Test
    fun buildsContiguousDaysFromTotalsAndSnapshots() = runTest {
        DatasetA.entries.forEach(intake::put)
        DatasetA.saveGoals(goals, DatasetA.first, LocalDate.of(2026, 3, 8))
        val days = useCase(DatasetA.first, DatasetA.last)
        assertThat(days.map { it.totalMl }).isEqualTo(DatasetA.totals)
        assertThat(days[2]).isEqualTo(DayStat(LocalDate.of(2026, 3, 4), 0, 2000, 0))
        assertThat(days[0].entryCount).isEqualTo(3)
        assertThat(days[7].goalMl).isNull() // no snapshot for Monday 09
    }

    @Test
    fun emptyWhenTheRangeIsBackwards() = runTest {
        assertThat(useCase(DatasetA.last, DatasetA.first)).isEmpty()
    }

    @Test
    fun observeReEmitsOnNewEntriesAndGoals() = runTest {
        useCase.observe(DatasetA.first, DatasetA.first).test {
            assertThat(awaitItem().single().totalMl).isEqualTo(0)
            intake.put(DatasetA.entry(DatasetA.first, 9, 500))
            assertThat(awaitItem().single().totalMl).isEqualTo(500)
            goals.saveSnapshot(DatasetA.goal(DatasetA.first, 2500))
            assertThat(awaitItem().single().goalMl).isEqualTo(2500)
        }
    }
}
