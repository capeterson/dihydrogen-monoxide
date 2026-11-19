package casa.peterson.dhmo.feature.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.domain.pace.Pace
import casa.peterson.dhmo.core.domain.pace.PaceStatus
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.core.testing.TestData
import casa.peterson.dhmo.core.testing.renderScreenVariants
import java.time.Instant
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith

private fun capture(name: String, state: TodayUiState.Ready) {
    renderScreenVariants("Today_$name") {
        TodayContent(
            state, onQuickAdd = {}, onCustomAdd = { _, _ -> }, onDelete = {}, onEdit = {}, onOpenGoal = {},
            onOpenReminders = {}, onOpenHistoryDay = {}, onContainers = {}, onBatteryHelp = {},
        )
    }
}

private fun state() = TodayUiState.Ready(
    day = LocalDate.of(2026, 6, 10), consumedMl = 0, goalMl = 2000, calculatedMl = 2000,
    goalMode = GoalMode.CALCULATED, pace = null, containers = TestData.defaultContainers(VolumeUnit.ML),
    entries = emptyList(), units = VolumeUnit.ML, showBatteryHint = false, showDisclaimerNote = false,
)

@RunWith(AndroidJUnit4::class)
class TodayEmptyRenderTest {
    @Test fun captureScreen() = capture("empty", state())
}

@RunWith(AndroidJUnit4::class)
class TodayBehindRenderTest {
    @Test fun captureScreen() = capture(
        "behind",
        state().copy(
            consumedMl = 750,
            pace = Pace(1100, 350, PaceStatus.BEHIND),
            entries = listOf(TestData.entry(Instant.parse("2026-06-10T08:00:00Z"))),
        ),
    )
}

@RunWith(AndroidJUnit4::class)
class TodayMetRenderTest {
    @Test fun captureScreen() = capture("met", state().copy(consumedMl = 2200))
}

@RunWith(AndroidJUnit4::class)
class TodayOverrideRenderTest {
    @Test fun captureScreen() = capture("override", state().copy(consumedMl = 500, goalMode = GoalMode.DAY_OVERRIDE))
}

@RunWith(AndroidJUnit4::class)
class TodayBatteryRenderTest {
    @Test fun captureScreen() = capture("battery", state().copy(showBatteryHint = true))
}
