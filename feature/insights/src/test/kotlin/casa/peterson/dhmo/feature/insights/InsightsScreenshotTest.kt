package casa.peterson.dhmo.feature.insights

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.testing.captureScreenVariants
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

// Each range over dataset A in light, dark and large font. One class per range, so each gets a fresh renderer.

private fun capture(name: String, selection: RangeSelection, picker: Boolean = false) = captureScreenVariants(name) {
    InsightsContent(DatasetA.ready(selection), InsightsActions(), showRangePicker = picker)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InsightsDayScreenshotTest {
    @Test fun day() = capture("InsightsDay", RangeSelection.forRange(InsightsRange.DAY, DatasetA.last))
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InsightsWeekScreenshotTest {
    @Test fun week() = capture("InsightsWeek", RangeSelection.forRange(InsightsRange.WEEK, DatasetA.last))
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InsightsMonthScreenshotTest {
    @Test fun month() = capture("InsightsMonth", RangeSelection.forRange(InsightsRange.MONTH, DatasetA.last))
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InsightsYearScreenshotTest {
    @Test fun year() = capture("InsightsYear", RangeSelection.forRange(InsightsRange.YEAR, DatasetA.last))
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InsightsCustomScreenshotTest {
    @Test fun custom() = capture(
        "InsightsCustom",
        RangeSelection(InsightsRange.CUSTOM, DatasetA.first.plusDays(7), DatasetA.last, compare = true),
    )

    @Test fun picker() = capture(
        "InsightsCustomPicker",
        RangeSelection(InsightsRange.CUSTOM, DatasetA.first, DatasetA.last),
        picker = true,
    )
}
