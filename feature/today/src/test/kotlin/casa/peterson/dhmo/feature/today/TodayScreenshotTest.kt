package casa.peterson.dhmo.feature.today

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.domain.pace.Pace
import casa.peterson.dhmo.core.domain.pace.PaceStatus
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.core.testing.TestData
import casa.peterson.dhmo.core.testing.captureScreenVariants
import java.time.Instant
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

private fun capture(name: String, state: TodayUiState.Ready) {
    captureScreenVariants("Today_$name") {
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
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TodayEmptyScreenshotTest {
    @Test fun captureScreen() = capture("empty", state())
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TodayBehindScreenshotTest {
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
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TodayMetScreenshotTest {
    @Test fun captureScreen() = capture("met", state().copy(consumedMl = 2200))
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TodayOverrideScreenshotTest {
    @Test fun captureScreen() = capture("override", state().copy(consumedMl = 500, goalMode = GoalMode.DAY_OVERRIDE))
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TodayBatteryScreenshotTest {
    @Test fun captureScreen() = capture("battery", state().copy(showBatteryHint = true))
}
