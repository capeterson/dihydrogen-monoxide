package casa.peterson.dhmo.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.hasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasClickAction
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasStartActivityClickAction
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import org.junit.Test
import org.junit.runner.RunWith

/** Each layout, rendered by Glance's unit-test environment from a fixed [WidgetModel]: its text, its buttons and their descriptions. */
@RunWith(AndroidJUnit4::class)
class WidgetLayoutsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val compactSize = DpSize(130.dp, 102.dp)
    private val wideStripSize = DpSize(203.dp, 102.dp)
    private val tallStripSize = DpSize(57.dp, 338.dp)
    private val squareSize = DpSize(130.dp, 220.dp)
    private val wideCardSize = DpSize(276.dp, 220.dp)
    private val tallCardSize = DpSize(130.dp, 455.dp)

    private fun model(sample: WidgetSample = WidgetSample.BEHIND) = sampleModel(context.resources, sample, VolumeUnit.ML)

    /** Renders [model] at [size] the way the launcher would: composed at the breakpoint that fits, not at the real size. */
    private fun render(model: WidgetModel, size: DpSize, expected: WidgetLayout, checks: GlanceAppWidgetUnitTest.() -> Unit) {
        val breakpoint = selectBreakpoint(size)
        org.junit.Assert.assertEquals(expected, breakpoint.layout)
        runGlanceAppWidgetUnitTest {
            setContext(context)
            setAppWidgetSize(breakpoint.size)
            provideComposable { WidgetRoot(model) }
            checks()
        }
    }

    private fun GlanceAppWidgetUnitTest.assertAddButtons(model: WidgetModel, count: Int) {
        onAllNodes(hasContentDescription("Add ")).assertCountEquals(count)
        model.buttons.take(count).forEach { button ->
            val action = hasRunCallbackClickAction<LogContainerAction>(actionParametersOf(ContainerIdKey to button.containerId.toString()))
            onNode(hasContentDescriptionEqualTo(button.contentDescription) and action).assertExists()
        }
    }

    private fun GlanceAppWidgetUnitTest.assertOpensAppWhenTappedElsewhere() {
        val open = hasStartActivityClickAction(ComponentName(context.packageName, "casa.peterson.dhmo.MainActivity"))
        onNode(open).assertExists()
    }

    @Test
    fun `compact shows the combined amount, the progress description and one add button`() {
        val model = model()
        render(model, compactSize, WidgetLayout.COMPACT) {
            onNode(hasTextEqualTo("1,250 / 2,500 ml")).assertExists()
            onNode(hasContentDescriptionEqualTo("1,250 ml of 2,500 ml, 50 percent")).assertExists()
            onNode(hasTextEqualTo("+250 ml")).assertExists()
            assertAddButtons(model, 1)
            assertOpensAppWhenTappedElsewhere()
        }
    }

    @Test
    fun `wide strip shows the amount, the goal and the default container's short label`() {
        val model = model()
        render(model, wideStripSize, WidgetLayout.WIDE_STRIP) {
            onNode(hasTextEqualTo("1,250 ml")).assertExists()
            onNode(hasTextEqualTo("of 2,500 ml")).assertExists()
            onNode(hasTextEqualTo("+250")).assertExists()
            assertAddButtons(model, 1)
        }
    }

    @Test
    fun `tall strip stacks the amount, the unit and the goal above one add button`() {
        val model = model()
        render(model, tallStripSize, WidgetLayout.TALL_STRIP) {
            onNode(hasTextEqualTo("1,250")).assertExists()
            onNode(hasTextEqualTo("ml")).assertExists()
            onNode(hasTextEqualTo("of 2,500 ml")).assertExists()
            onNode(hasTextEqualTo("+250")).assertExists()
            onNode(hasContentDescription("50 percent")).assertExists()
            assertAddButtons(model, 1)
        }
    }

    @Test
    fun `square shows the amount, the percentage and one add button`() {
        val model = model()
        render(model, squareSize, WidgetLayout.SQUARE) {
            onNode(hasTextEqualTo("1,250 ml")).assertExists()
            onNode(hasTextEqualTo("50% of 2,500 ml")).assertExists()
            onNode(hasTextEqualTo("+250 ml")).assertExists()
            assertAddButtons(model, 1)
        }
    }

    @Test
    fun `wide card shows the header, the last drink, the detail line and three buttons`() {
        val model = model()
        render(model, wideCardSize, WidgetLayout.WIDE_CARD) {
            onNode(hasTextEqualTo("Today")).assertExists()
            onNode(hasTextEqualTo("last drink 2:15 PM")).assertExists()
            onNode(hasTextEqualTo("1,250 ml")).assertExists()
            onNode(hasTextEqualTo("of 2,500 ml · 50% · 250 ml behind")).assertExists()
            listOf("+250 ml", "+350 ml", "+500 ml").forEach { onNode(hasTextEqualTo(it)).assertExists() }
            assertAddButtons(model, 3)
        }
    }

    @Test
    fun `tall card puts the goal, percentage and pace on their own lines above three stacked buttons`() {
        val model = model()
        render(model, tallCardSize, WidgetLayout.TALL_CARD) {
            onNode(hasTextEqualTo("Today")).assertExists()
            onNode(hasTextEqualTo("last drink 2:15 PM")).assertExists()
            onNode(hasTextEqualTo("1,250 ml")).assertExists()
            onNode(hasTextEqualTo("of 2,500 ml")).assertExists()
            onNode(hasTextEqualTo("50%")).assertExists()
            onNode(hasTextEqualTo("250 ml behind")).assertExists()
            assertAddButtons(model, 3)
        }
    }

    @Test
    fun `cards show only the containers that exist`() {
        val two = model(WidgetSample.FEW_CONTAINERS)
        render(two, wideCardSize, WidgetLayout.WIDE_CARD) { assertAddButtons(two, 2) }
        render(two, tallCardSize, WidgetLayout.TALL_CARD) { assertAddButtons(two, 2) }
    }

    @Test
    fun `with no containers the button opens the app instead of logging`() {
        val model = model(WidgetSample.NO_CONTAINERS)
        render(model, wideCardSize, WidgetLayout.WIDE_CARD) {
            onAllNodes(hasContentDescription("Add ")).assertCountEquals(0)
            onNode(hasTextEqualTo("Open app")).assertExists()
            onNode(hasContentDescriptionEqualTo("Open the app to add a drink container") and hasClickAction()).assertExists()
        }
    }

    @Test
    fun `without a goal only the amount shows`() {
        val model = model(WidgetSample.NO_GOAL)
        render(model, wideCardSize, WidgetLayout.WIDE_CARD) {
            onNode(hasTextEqualTo("750 ml")).assertExists()
            onAllNodes(hasText("of ")).assertCountEquals(0)
            onAllNodes(hasText("%")).assertCountEquals(0)
        }
        render(model, tallStripSize, WidgetLayout.TALL_STRIP) {
            onAllNodes(hasText("of ")).assertCountEquals(0)
        }
    }

    @Test
    fun `unfinished setup shows an invitation in every layout and no add buttons`() {
        val model = model(WidgetSample.ONBOARDING)
        listOf(
            compactSize to WidgetLayout.COMPACT,
            wideStripSize to WidgetLayout.WIDE_STRIP,
            tallStripSize to WidgetLayout.TALL_STRIP,
            squareSize to WidgetLayout.SQUARE,
            wideCardSize to WidgetLayout.WIDE_CARD,
            tallCardSize to WidgetLayout.TALL_CARD,
        ).forEach { (size, layout) ->
            render(model, size, layout) {
                onNode(hasTextEqualTo("Finish setup in the app")).assertExists()
                onAllNodes(hasContentDescription("Add ")).assertCountEquals(0)
                assertOpensAppWhenTappedElsewhere()
            }
        }
    }

    @Test
    fun `outside the wake window the pace text is absent`() {
        val model = model(WidgetSample.OUTSIDE_WAKE_WINDOW)
        render(model, wideCardSize, WidgetLayout.WIDE_CARD) {
            onNode(hasTextEqualTo("of 2,500 ml · 50%")).assertExists()
            onAllNodes(hasText("behind")).assertCountEquals(0)
        }
    }

    @Test
    fun `reaching the goal says so in the progress description`() {
        val model = model(WidgetSample.GOAL_REACHED)
        render(model, compactSize, WidgetLayout.COMPACT) {
            onNode(hasContentDescriptionEqualTo("2,600 ml of 2,500 ml, goal reached")).assertExists()
        }
    }
}
