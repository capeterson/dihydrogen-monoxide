package casa.peterson.dhmo.feature.today

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.ReceiveTurbine
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
import casa.peterson.dhmo.core.model.settings.ThemeMode
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
    private val device = FakeDeviceStateRepository()
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
            device,
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

    @Test
    fun confirmedLogsProduceSipCuesButLoadingExistingWaterDoesNot() = runTest {
        goals.saveSnapshot(goal())
        intake.put(TestData.entry(clock.instant(), 250))
        vm.uiState.test {
            val initial = ready { it.consumedMl == 250 }
            assertThat(initial.sipEvent).isEqualTo(0L)
            assertThat(initial.goalEvent).isEqualTo(0L)
            vm.onCustomAdd(250)
            val logged = ready { it.sipEvent > 0L }
            assertThat(logged.consumedMl).isEqualTo(500)
            assertThat(logged.goalEvent).isEqualTo(0L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun goalCelebrationOnlyHappensOnFirstConfirmedCrossingEvenAfterDeleteAndRelog() = runTest {
        goals.saveSnapshot(goal())
        vm.uiState.test {
            ready()
            vm.onCustomAdd(2000)
            val met = ready { it.goalEvent > 0L }
            assertThat(met.consumedMl).isEqualTo(2000)
            assertThat(device.current().lastCelebratedGoalDay).isEqualTo(goal().date.toEpochDay())
            vm.onDelete(met.entries.single().id)
            ready { it.consumedMl == 0 }
            vm.onCustomAdd(2000)
            val again = ready { it.sipEvent > met.sipEvent }
            assertThat(again.goalEvent).isEqualTo(met.goalEvent)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun persistedCelebrationMarkerPreventsReplayAfterRecreation() = runTest {
        goals.saveSnapshot(goal())
        intake.put(TestData.entry(clock.instant(), 1750))
        device.update { it.copy(lastCelebratedGoalDay = goal().date.toEpochDay()) }
        vm.uiState.test {
            ready()
            vm.onCustomAdd(250)
            val logged = ready { it.sipEvent > 0L }
            assertThat(logged.consumedMl).isEqualTo(2000)
            assertThat(logged.goalEvent).isEqualTo(0L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun loweringGoalDoesNotCreateACelebration() = runTest {
        goals.saveSnapshot(goal())
        intake.put(TestData.entry(clock.instant(), 1000))
        vm.uiState.test {
            ready()
            goals.saveSnapshot(goal().copy(targetMl = 500))
            val changed = ready { it.goalMl == 500 }
            assertThat(changed.sipEvent).isEqualTo(0L)
            assertThat(changed.goalEvent).isEqualTo(0L)
            assertThat(device.current().lastCelebratedGoalDay).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun rapidTapsProduceOneGoalCrossingAndKeepEveryDrink() = runTest {
        goals.saveSnapshot(goal())
        vm.uiState.test {
            ready()
            repeat(10) { vm.onCustomAdd(250) }
            val logged = ready { it.sipEvent == 10L }
            assertThat(logged.consumedMl).isEqualTo(2500)
            assertThat(logged.entries).hasSize(10)
            assertThat(logged.goalEvent).isEqualTo(8L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun dayNightChoicePersistsForTheWholeApp() = runTest {
        vm.setNightMode(true)
        assertThat(settings.current().theme.mode).isEqualTo(ThemeMode.DARK)
        vm.setNightMode(false)
        assertThat(settings.current().theme.mode).isEqualTo(ThemeMode.LIGHT)
    }

    private suspend fun ReceiveTurbine<TodayUiState>.ready(predicate: (TodayUiState.Ready) -> Boolean = { true }): TodayUiState.Ready {
        while (true) {
            val next = awaitItem()
            if (next is TodayUiState.Ready && predicate(next)) return next
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
