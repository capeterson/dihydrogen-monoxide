package casa.peterson.dhmo.feature.today

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.data.time.DayTicker
import casa.peterson.dhmo.core.data.usecase.intake.DeleteIntakeUseCase
import casa.peterson.dhmo.core.data.usecase.intake.LogIntakeUseCase
import casa.peterson.dhmo.core.data.usecase.intake.UndoDeleteIntakeUseCase
import casa.peterson.dhmo.core.data.usecase.today.ObserveTodayUseCase
import casa.peterson.dhmo.core.model.ClampFlag
import casa.peterson.dhmo.core.model.DailyGoal
import casa.peterson.dhmo.core.model.GoalBreakdown
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.core.testing.FakeContainerRepository
import casa.peterson.dhmo.core.testing.FakeDeviceStateRepository
import casa.peterson.dhmo.core.testing.FakeGoalRepository
import casa.peterson.dhmo.core.testing.FakeIntakeRepository
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.FixedZoneProvider
import casa.peterson.dhmo.core.testing.MutableClock
import casa.peterson.dhmo.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodayScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun quickAddAndSwipeInvokeTheirActions() {
        val entry = TestData.entry(Instant.parse("2026-06-10T08:00:00Z"))
        val container = TestData.defaultContainers(VolumeUnit.ML).first()
        val state = TodayUiState.Ready(
            LocalDate.of(2026, 6, 10), 250, 2000, 2000, GoalMode.CALCULATED, null,
            listOf(container), listOf(entry), VolumeUnit.ML, false, false,
        )
        var added: Int? = null
        var deleted: UUID? = null
        compose.setContent {
            MaterialTheme {
                TodayContent(
                    state, onQuickAdd = { added = it.volumeMl }, onCustomAdd = { _, _ -> }, onDelete = { deleted = it },
                    onEdit = {}, onOpenGoal = {}, onOpenReminders = {}, onOpenHistoryDay = {}, onContainers = {}, onBatteryHelp = {},
                )
            }
        }
        compose.onNodeWithTag("today_list").performScrollToIndex(1)
        compose.onNodeWithTag("quick_add_${container.id}").performClick()
        compose.runOnIdle { assertThat(added).isEqualTo(250) }
        compose.onNodeWithTag("today_list").performScrollToIndex(2)
        compose.onNodeWithTag("today_entry_${entry.id}").performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { assertThat(deleted).isEqualTo(entry.id) }
    }

    @Test
    fun swipeDeleteShowsUndoAndRestoresEntry() {
        val clock = MutableClock(Instant.parse("2026-06-10T12:00:00Z"))
        val zone = FixedZoneProvider()
        val settings = FakeSettingsRepository()
        val intake = FakeIntakeRepository(clock, zone)
        val goals = FakeGoalRepository(clock)
        val entry = TestData.entry(clock.instant())
        intake.put(entry)
        runBlocking {
            goals.saveSnapshot(
                DailyGoal(
                    LocalDate.of(2026, 6, 10),
                    2000,
                    2000,
                    GoalMode.CALCULATED,
                    GoalBreakdown(1, 36, emptyList(), 2000, 2000, 2000, ClampFlag.NONE),
                    0,
                    1,
                    clock.instant(),
                ),
            )
        }
        val ticker = DayTicker(ApplicationProvider.getApplicationContext<Context>(), clock, zone, settings)
        val vm = TodayViewModel(
            ObserveTodayUseCase(ticker, intake, goals, settings, clock, zone),
            FakeContainerRepository(TestData.defaultContainers(VolumeUnit.ML)),
            settings,
            FakeDeviceStateRepository(),
            LogIntakeUseCase(intake),
            DeleteIntakeUseCase(intake),
            UndoDeleteIntakeUseCase(intake),
            clock,
        )
        compose.setContent {
            MaterialTheme {
                TodayScreen({}, {}, {}, {}, {}, {}, vm)
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("today_list")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("today_list").performScrollToIndex(2)
        compose.onNodeWithTag("today_entry_${entry.id}").performTouchInput { swipeLeft() }
        compose.waitUntil(5_000) { runBlocking { intake.get(entry.id)?.deletedAt != null } }
        compose.waitUntil(5_000) { (vm.uiState.value as? TodayUiState.Ready)?.entries?.none { it.id == entry.id } == true }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(5_000) { runBlocking { intake.get(entry.id)?.deletedAt == null } }
        compose.waitUntil(5_000) { (vm.uiState.value as? TodayUiState.Ready)?.entries?.any { it.id == entry.id } == true }
        compose.onNodeWithTag("today_list").performScrollToIndex(2)
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag("today_entry_${entry.id}")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("today_entry_${entry.id}").assertExists()
        compose.onNodeWithTag("today_list").performScrollToIndex(1)
        val container = TestData.defaultContainers(VolumeUnit.ML).first()
        compose.onNodeWithTag("quick_add_${container.id}").performClick()
        compose.waitUntil(5_000) { intake.all.size == 2 }
        assertThat(intake.all.sumOf { it.volumeMl }).isEqualTo(entry.volumeMl + container.volumeMl)
    }
}
