package casa.peterson.dhmo.feature.today

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import casa.peterson.dhmo.core.data.time.DayTicker
import casa.peterson.dhmo.core.data.usecase.intake.DeleteIntakeUseCase
import casa.peterson.dhmo.core.data.usecase.intake.LogIntakeUseCase
import casa.peterson.dhmo.core.data.usecase.intake.UndoDeleteIntakeUseCase
import casa.peterson.dhmo.core.data.usecase.today.ObserveTodayUseCase
import casa.peterson.dhmo.core.domain.pace.PaceStatus
import casa.peterson.dhmo.core.model.ClampFlag
import casa.peterson.dhmo.core.model.DailyGoal
import casa.peterson.dhmo.core.model.GoalBreakdown
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.testing.FakeContainerRepository
import casa.peterson.dhmo.core.testing.FakeDeviceStateRepository
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
class TodayViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val clock = MutableClock(Instant.parse("2026-06-10T12:00:00Z"))
    private val zone = FixedZoneProvider(ZoneOffset.UTC)
    private val settings = FakeSettingsRepository()
    private val intake = FakeIntakeRepository(clock, zone)
    private val goals = FakeGoalRepository(clock)
    private val containers = FakeContainerRepository(TestData.defaultContainers(casa.peterson.dhmo.core.model.settings.VolumeUnit.ML))
    private val ticker = DayTicker(ApplicationProvider.getApplicationContext<Context>(), clock, zone, settings)
    private val observe = ObserveTodayUseCase(ticker, intake, goals, settings, clock, zone)
    private val vm by lazy {
        TodayViewModel(
            observe,
            containers,
            settings,
            FakeDeviceStateRepository(),
            LogIntakeUseCase(intake),
            DeleteIntakeUseCase(intake),
            UndoDeleteIntakeUseCase(intake),
            clock,
        )
    }

    @Test
    fun quickAddDeleteAndUndo() = runTest {
        goals.saveSnapshot(goal())
        vm.uiState.test {
            var ready = awaitItem()
            while (ready !is TodayUiState.Ready) ready = awaitItem()
            val container = ready.containers.first()
            vm.onQuickAdd(container)
            while ((ready as TodayUiState.Ready).entries.isEmpty()) ready = awaitItem()
            val id = ready.entries.single().id
            vm.effects.test {
                vm.onDelete(id)
                assertThat(awaitItem()).isEqualTo(TodayEffect.EntryDeleted(id, container.volumeMl, container.name))
                vm.onUndoDelete(id)
                assertThat(intake.get(id)?.deletedAt).isNull()
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun customAddUsesRequestedAmountAndTime() = runTest {
        val at = clock.instant().minusSeconds(60)
        vm.onCustomAdd(375, at)
        assertThat(intake.all.single().volumeMl).isEqualTo(375)
        assertThat(intake.all.single().instant).isEqualTo(at)
    }

    @Test
    fun goalModesAndPaceReachTheScreen() = runTest {
        goals.saveSnapshot(goal())
        vm.uiState.test {
            var ready = awaitItem()
            while (ready !is TodayUiState.Ready) ready = awaitItem()
            assertThat(ready.goalMode).isEqualTo(GoalMode.CALCULATED)
            assertThat(ready.pace).isNotNull()
            goals.saveSnapshot(goal().copy(mode = GoalMode.DAY_OVERRIDE, targetMl = 2500))
            do {
                ready = awaitItem()
            } while (ready !is TodayUiState.Ready || ready.goalMode != GoalMode.DAY_OVERRIDE)
            assertThat(ready.goalMl).isEqualTo(2500)
            goals.saveSnapshot(goal().copy(mode = GoalMode.GLOBAL_OVERRIDE, targetMl = 2200))
            do {
                ready = awaitItem()
            } while (ready !is TodayUiState.Ready || ready.goalMode != GoalMode.GLOBAL_OVERRIDE)
            assertThat(ready.goalMl).isEqualTo(2200)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun paceChangesFromBehindToOnTrackToAhead() = runTest {
        goals.saveSnapshot(goal())
        vm.uiState.test {
            var ready = awaitItem()
            while (ready !is TodayUiState.Ready) ready = awaitItem()
            val expected = requireNotNull(ready.pace).expectedMl
            assertThat(ready.pace.status).isEqualTo(PaceStatus.BEHIND)
            vm.onCustomAdd(expected)
            do {
                ready = awaitItem()
            } while (ready !is TodayUiState.Ready || ready.pace?.status != PaceStatus.ON_TRACK)
            vm.onCustomAdd(1000)
            do {
                ready = awaitItem()
            } while (ready !is TodayUiState.Ready || ready.pace?.status != PaceStatus.AHEAD)
            assertThat(ready.consumedMl).isEqualTo(expected + 1000)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun invalidAmountProducesError() = runTest {
        vm.effects.test {
            vm.onCustomAdd(0)
            assertThat(awaitItem()).isEqualTo(TodayEffect.Error(R.string.feature_today_invalid_amount))
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun goal() = DailyGoal(
        LocalDate.of(2026, 6, 10),
        2000,
        2000,
        GoalMode.CALCULATED,
        GoalBreakdown(1, 36, emptyList(), 2000, 2000, 2000, ClampFlag.NONE),
        0,
        1,
        clock.instant(),
    )
}
