package casa.peterson.dhmo.feature.insights

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.testing.renderScreenVariants
import org.junit.Test
import org.junit.runner.RunWith

// Each range over dataset A in light, dark and large font. One class per range, so each gets a fresh renderer.

private fun capture(name: String, selection: RangeSelection, picker: Boolean = false) = renderScreenVariants(name) {
    InsightsContent(DatasetA.ready(selection), InsightsActions(), showRangePicker = picker)
}

@RunWith(AndroidJUnit4::class)
class InsightsDayRenderTest {
    @Test fun day() = capture("InsightsDay", RangeSelection.forRange(InsightsRange.DAY, DatasetA.last))
}

@RunWith(AndroidJUnit4::class)
class InsightsWeekRenderTest {
    @Test fun week() = capture("InsightsWeek", RangeSelection.forRange(InsightsRange.WEEK, DatasetA.last))
}

@RunWith(AndroidJUnit4::class)
class InsightsMonthRenderTest {
    @Test fun month() = capture("InsightsMonth", RangeSelection.forRange(InsightsRange.MONTH, DatasetA.last))
}

@RunWith(AndroidJUnit4::class)
class InsightsYearRenderTest {
    @Test fun year() = capture("InsightsYear", RangeSelection.forRange(InsightsRange.YEAR, DatasetA.last))
}

@RunWith(AndroidJUnit4::class)
class InsightsCustomRenderTest {
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
