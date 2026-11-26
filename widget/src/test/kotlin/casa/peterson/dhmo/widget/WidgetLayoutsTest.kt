package casa.peterson.dhmo.widget

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
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
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Each layout, rendered by Glance's unit-test environment from a fixed [WidgetModel]: its text, coasters and their actions. */
@RunWith(AndroidJUnit4::class)
class WidgetLayoutsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    // Portrait cells (73n - 16 wide, 118n - 16 tall) and one landscape cell for each short variant.
    private val compact = DpSize(130.dp, 102.dp)
    private val compactLandscape = DpSize(269.dp, 51.dp)
    private val wideStrip = DpSize(203.dp, 102.dp)
    private val wideStripLandscape = DpSize(412.dp, 51.dp)
    private val tallStrip = DpSize(57.dp, 338.dp)
    private val square = DpSize(130.dp, 220.dp)
    private val wideCard = DpSize(276.dp, 220.dp)
    private val wideCardLandscape = DpSize(554.dp, 117.dp)
    private val tallCard = DpSize(130.dp, 455.dp)

    private val portraitCells = listOf(compact, wideStrip, tallStrip, square, wideCard, tallCard)

    /** The drawings are not what these tests check, so a one-pixel stand-in keeps them fast. */
    private val art = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).let { WidgetArt(it, it) }

    private fun model(sample: WidgetSample = WidgetSample.BEHIND) = sampleModel(context.resources, sample)

    /** Renders [model] the way the launcher would for a cell of [size]: composed at the breakpoint that fits it. */
    private fun render(model: WidgetModel, size: DpSize, checks: GlanceAppWidgetUnitTest.() -> Unit) {
        val breakpoint = selectBreakpoint(size)
        runGlanceAppWidgetUnitTest {
            setContext(context)
            setAppWidgetSize(breakpoint.size)
            provideComposable { WidgetRoot(model, art) }
            checks()
        }
    }

    private fun GlanceAppWidgetUnitTest.assertCoasters(model: WidgetModel, count: Int) {
        onAllNodes(hasContentDescription("Add ")).assertCountEquals(count)
        model.buttons.take(count).forEach { button ->
            val logs = hasRunCallbackClickAction<LogContainerAction>(actionParametersOf(ContainerIdKey to button.containerId.toString()))
            onNode(hasContentDescriptionEqualTo(button.contentDescription) and logs).assertExists()
        }
    }

    private fun GlanceAppWidgetUnitTest.assertOpensAppWhenTappedElsewhere() {
        onNode(hasStartActivityClickAction(ComponentName(context.packageName, "casa.peterson.dhmo.MainActivity"))).assertExists()
    }

    private fun GlanceAppWidgetUnitTest.assertJarDescribed(model: WidgetModel) {
        onNode(hasContentDescriptionEqualTo(model.progressDescription)).assertExists()
    }

    @Test
    fun `compact shows the total and one coaster`() {
        val model = model()
        render(model, compact) {
            onNode(hasTextEqualTo("1,250")).assertExists()
            onNode(hasTextEqualTo("of 2,500 ml")).assertExists()
            assertCoasters(model, 1)
            assertOpensAppWhenTappedElsewhere()
        }
        render(model, compactLandscape) {
            assertJarDescribed(model)
            assertCoasters(model, 1)
        }
    }

    @Test
    fun `wide strip shows the jar, the total, the pace and one coaster, also folded to one line in landscape`() {
        val model = model()
        render(model, wideStrip) {
            assertJarDescribed(model)
            onNode(hasTextEqualTo("1,250")).assertExists()
            onNode(hasTextEqualTo("of 2,500 ml")).assertExists()
            onNode(hasTextEqualTo("250 ml behind")).assertExists()
            assertCoasters(model, 1)
        }
        render(model, wideStripLandscape) {
            onNode(hasTextEqualTo("/ 2,500 ml")).assertExists()
            onNode(hasTextEqualTo("250 ml behind")).assertExists()
            assertCoasters(model, 1)
        }
    }

    @Test
    fun `tall strip shows the same as the wide strip, stacked`() {
        val model = model()
        render(model, tallStrip) {
            assertJarDescribed(model)
            onNode(hasTextEqualTo("1,250")).assertExists()
            onNode(hasTextEqualTo("of 2,500 ml")).assertExists()
            onNode(hasTextEqualTo("250 ml behind")).assertExists()
            assertCoasters(model, 1)
        }
    }

    @Test
    fun `square shows the window, the total, the pace and two coasters`() {
        val model = model()
        render(model, square) {
            assertJarDescribed(model)
            onNode(hasTextEqualTo("/ 2,500 ml")).assertExists()
            onNode(hasTextEqualTo("250 ml behind")).assertExists()
            assertCoasters(model, 2)
        }
    }

    @Test
    fun `wide card shows the heading, the last drink, the total, the pace and three coasters, also in landscape`() {
        val model = model()
        render(model, wideCard) {
            assertJarDescribed(model)
            onNode(hasTextEqualTo("Today")).assertExists()
            onNode(hasTextEqualTo("Last drink 2:15 PM")).assertExists()
            onNode(hasTextEqualTo("1,250")).assertExists()
            onNode(hasTextEqualTo("/ 2,500 ml")).assertExists()
            onNode(hasTextEqualTo("250 ml behind pace")).assertExists()
            listOf("Glass", "Mug", "Bottle").forEach { onNode(hasTextEqualTo(it)).assertExists() }
            assertCoasters(model, 3)
        }
        render(model, wideCardLandscape) { assertCoasters(model, 3) }
    }

    @Test
    fun `tall card shows the same as the wide card, stacked`() {
        val model = model()
        render(model, tallCard) {
            assertJarDescribed(model)
            onNode(hasTextEqualTo("Today")).assertExists()
            onNode(hasTextEqualTo("Last drink 2:15 PM")).assertExists()
            onNode(hasTextEqualTo("/ 2,500 ml")).assertExists()
            onNode(hasTextEqualTo("250 ml behind pace")).assertExists()
            assertCoasters(model, 3)
        }
    }

    @Test
    fun `cards show only the containers that exist`() {
        val two = model(WidgetSample.FEW_CONTAINERS)
        render(two, wideCard) { assertCoasters(two, 2) }
        render(two, tallCard) { assertCoasters(two, 2) }
    }

    @Test
    fun `with no containers every layout's coaster opens the app instead of logging`() {
        val model = model(WidgetSample.NO_CONTAINERS)
        portraitCells.forEach { size ->
            render(model, size) {
                onAllNodes(hasContentDescription("Add ")).assertCountEquals(0)
                onNode(hasContentDescriptionEqualTo("Open the app to add a drink container") and hasClickAction()).assertExists()
            }
        }
    }

    @Test
    fun `just after a widget log every layout offers Undo for that entry`() {
        val model = model(WidgetSample.JUST_ADDED)
        val entry = checkNotNull(model.undo).entryId
        val undoes = hasRunCallbackClickAction<UndoLastDrinkAction>(actionParametersOf(EntryIdKey to entry.toString()))
        portraitCells.forEach { size ->
            render(model, size) {
                onNode(hasContentDescriptionEqualTo("Undo adding 250 ml") and undoes).assertExists()
                // Undo never replaces a coaster, so a second tap on a coaster logs a second glass.
                assertCoasters(model, model.buttons.size.coerceAtMost(coastersShownAt(size)))
            }
        }
    }

    @Test
    fun `without a goal only the amount shows`() {
        val model = model(WidgetSample.NO_GOAL)
        render(model, wideCard) {
            onNode(hasTextEqualTo("750 ml")).assertExists()
            onAllNodes(hasText("2,500")).assertCountEquals(0)
        }
        render(model, tallStrip) {
            onNode(hasTextEqualTo("750")).assertExists()
            onNode(hasTextEqualTo("ml")).assertExists()
            onAllNodes(hasText("2,500")).assertCountEquals(0)
        }
    }

    @Test
    fun `outside the wake window there is no pace line`() {
        val model = model(WidgetSample.OUTSIDE_WAKE_WINDOW)
        portraitCells.forEach { size -> render(model, size) { onAllNodes(hasText("behind")).assertCountEquals(0) } }
    }

    @Test
    fun `a met goal says so`() {
        val model = model(WidgetSample.GOAL_REACHED)
        render(model, wideCard) {
            onNode(hasTextEqualTo("Goal reached")).assertExists()
            onNode(hasContentDescriptionEqualTo("2,600 ml of 2,500 ml, goal reached")).assertExists()
        }
    }

    @Test
    fun `unfinished setup shows an invitation in every layout and no coasters`() {
        val model = model(WidgetSample.ONBOARDING)
        portraitCells.forEach { size ->
            render(model, size) {
                onNode(hasTextEqualTo("Set up your day")).assertExists()
                onNode(hasContentDescriptionEqualTo("Open the app to finish setting up")).assertExists()
                onAllNodes(hasContentDescription("Add ")).assertCountEquals(0)
                assertOpensAppWhenTappedElsewhere()
            }
        }
    }

    @Test
    fun `every portrait cell lands on its intended layout`() {
        assertThat(portraitCells.map(::layoutFor)).containsExactly(
            WidgetLayout.COMPACT,
            WidgetLayout.WIDE_STRIP,
            WidgetLayout.TALL_STRIP,
            WidgetLayout.SQUARE,
            WidgetLayout.WIDE_CARD,
            WidgetLayout.TALL_CARD,
        ).inOrder()
    }

    /** How many coasters a layout has room for: one in the strips and compact, two in the square, three in the cards. */
    private fun coastersShownAt(size: DpSize): Int = when (layoutFor(size)) {
        WidgetLayout.SQUARE -> 2
        WidgetLayout.WIDE_CARD, WidgetLayout.TALL_CARD -> WidgetModel.MAX_BUTTONS
        else -> 1
    }
}
