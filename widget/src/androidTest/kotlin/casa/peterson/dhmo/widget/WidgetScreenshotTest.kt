package casa.peterson.dhmo.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Draws the real widget layouts into bitmaps on a device, for the pull request's screenshots. It is not an assertion test:
 * it fails only if composition or inflation throws. Output goes to the test's additional-output directory, which Android Gradle Plugin
 * copies into `widget/build/outputs/.../additional_test_output/widget-screenshots`, one PNG per tile named
 * `<sheet>__<order>__<label>.png`, which a script tiles into contact sheets.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(AndroidJUnit4::class)
class WidgetScreenshotTest {
    private data class Tile(
        val sheet: String,
        val label: String,
        val layout: WidgetLayout,
        val sample: WidgetSample,
        val size: DpSize,
        val night: Boolean = false,
        val units: VolumeUnit = VolumeUnit.L,
    )

    // Portrait cell sizes from Android's widget guidance (73n - 16 wide, 118n - 16 tall) and landscape (142n - 15, 66n - 15).
    private val compact = DpSize(130.dp, 102.dp)
    private val wideStrip = DpSize(203.dp, 102.dp)
    private val tallStrip = DpSize(57.dp, 338.dp)
    private val square = DpSize(130.dp, 220.dp)
    private val wideCard = DpSize(276.dp, 220.dp)
    private val tallCard = DpSize(130.dp, 455.dp)

    private fun tiles(): List<Tile> {
        val all = mutableListOf<Tile>()
        for (night in listOf(false, true)) {
            val sheet = if (night) "layouts-dark" else "layouts-light"
            all += Tile(sheet, "2x1 compact", WidgetLayout.COMPACT, WidgetSample.BEHIND, compact, night)
            all += Tile(sheet, "3x1 wide strip", WidgetLayout.WIDE_STRIP, WidgetSample.BEHIND, wideStrip, night)
            all += Tile(sheet, "1x3 tall strip", WidgetLayout.TALL_STRIP, WidgetSample.BEHIND, tallStrip, night)
            all += Tile(sheet, "2x2 square", WidgetLayout.SQUARE, WidgetSample.BEHIND, square, night)
            all += Tile(sheet, "4x2 wide card", WidgetLayout.WIDE_CARD, WidgetSample.BEHIND, wideCard, night)
            all += Tile(sheet, "2x4 tall card", WidgetLayout.TALL_CARD, WidgetSample.BEHIND, tallCard, night)
        }
        val cardStates = listOf(
            "goal reached" to WidgetSample.GOAL_REACHED,
            "no goal" to WidgetSample.NO_GOAL,
            "no containers" to WidgetSample.NO_CONTAINERS,
            "setup not finished" to WidgetSample.ONBOARDING,
            "two containers" to WidgetSample.FEW_CONTAINERS,
            "outside wake window" to WidgetSample.OUTSIDE_WAKE_WINDOW,
        )
        for ((name, sample) in cardStates) {
            all += Tile("states-cards", "wide card, $name", WidgetLayout.WIDE_CARD, sample, wideCard)
            all += Tile("states-cards", "tall card, $name", WidgetLayout.TALL_CARD, sample, tallCard)
        }
        all += Tile("states-cards", "wide card, goal reached (dark)", WidgetLayout.WIDE_CARD, WidgetSample.GOAL_REACHED, wideCard, true)
        all += Tile("states-cards", "tall card, goal reached (dark)", WidgetLayout.TALL_CARD, WidgetSample.GOAL_REACHED, tallCard, true)
        for ((name, sample) in listOf("goal reached" to WidgetSample.GOAL_REACHED, "no goal" to WidgetSample.NO_GOAL)) {
            all += Tile("states-strips", "wide strip, $name", WidgetLayout.WIDE_STRIP, sample, wideStrip)
            all += Tile("states-strips", "tall strip, $name", WidgetLayout.TALL_STRIP, sample, tallStrip)
            all += Tile("states-strips", "wide strip, $name (dark)", WidgetLayout.WIDE_STRIP, sample, wideStrip, true)
            all += Tile("states-strips", "tall strip, $name (dark)", WidgetLayout.TALL_STRIP, sample, tallStrip, true)
        }
        for ((name, sample) in listOf("no containers" to WidgetSample.NO_CONTAINERS, "setup not finished" to WidgetSample.ONBOARDING)) {
            all += Tile("states-strips", "wide strip, $name", WidgetLayout.WIDE_STRIP, sample, wideStrip)
            all += Tile("states-strips", "tall strip, $name", WidgetLayout.TALL_STRIP, sample, tallStrip)
        }
        // Landscape cells and a different unit, which stress the narrow and short variants.
        val landscape = listOf(
            Triple("2x1 compact", WidgetLayout.COMPACT, DpSize(269.dp, 51.dp)),
            Triple("3x1 wide strip", WidgetLayout.WIDE_STRIP, DpSize(412.dp, 51.dp)),
            Triple("1x3 tall strip", WidgetLayout.TALL_STRIP, DpSize(127.dp, 184.dp)),
            Triple("2x2 square", WidgetLayout.SQUARE, DpSize(269.dp, 117.dp)),
            Triple("4x2 wide card", WidgetLayout.WIDE_CARD, DpSize(554.dp, 117.dp)),
            Triple("2x4 tall card", WidgetLayout.TALL_CARD, DpSize(269.dp, 250.dp)),
        )
        for ((name, layout, size) in landscape) {
            all += Tile("layouts-landscape", "$name (landscape)", layout, WidgetSample.BEHIND, size)
            all += Tile("layouts-landscape", "$name (landscape, dark)", layout, WidgetSample.BEHIND, size, true)
        }
        for ((name, layout, size) in listOf(
            Triple("2x1 compact", WidgetLayout.COMPACT, compact),
            Triple("2x2 square", WidgetLayout.SQUARE, square),
            Triple("4x2 wide card", WidgetLayout.WIDE_CARD, wideCard),
            Triple("2x4 tall card", WidgetLayout.TALL_CARD, tallCard),
        )) {
            all += Tile("units-floz", "$name, fl oz", layout, WidgetSample.BEHIND, size, units = VolumeUnit.US_FL_OZ)
        }
        return all
    }

    @Test
    fun renderEveryTile() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        // AGP copies this directory off the device after the run: build/outputs/managed_device_android_test_additional_output.
        val out = File("/sdcard/Android/media/${base.packageName}/additional_test_output/widget-screenshots").apply {
            deleteRecursively()
            mkdirs()
        }
        tiles().forEachIndexed { index, tile ->
            val bitmap = render(base, tile)
            val name = "${tile.sheet}__${index.toString().padStart(3, '0')}__${tile.label.replace(Regex("[^A-Za-z0-9 ,()-]"), "")}.png"
            File(out, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun render(base: Context, tile: Tile): Bitmap {
        val config = Configuration(base.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (tile.night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val context = base.createConfigurationContext(config)
        val density = context.resources.displayMetrics.density
        val model = sampleModel(context.resources, tile.sample, tile.units, Locale.US)
        // Compose at the breakpoint the launcher would choose, then draw it into the real cell size.
        val breakpoint = selectBreakpoint(tile.size)
        check(breakpoint.layout == tile.layout) { "${tile.label}: ${tile.size} selects ${breakpoint.layout}, expected ${tile.layout}" }
        val views = runBlocking {
            GlanceRemoteViews().compose(context, breakpoint.size, null, Bundle()) { WidgetRoot(model) }.remoteViews
        }
        val widthPx = (tile.size.width.value * density).roundToInt()
        val heightPx = (tile.size.height.value * density).roundToInt()
        val margin = (MARGIN_DP * density).roundToInt()
        val bitmap = Bitmap.createBitmap(widthPx + margin * 2, heightPx + margin * 2, Bitmap.Config.ARGB_8888)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val parent = FrameLayout(context)
            val view: View = views.apply(context, parent)
            view.measure(
                MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(heightPx, MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, widthPx, heightPx)
            val canvas = Canvas(bitmap)
            canvas.drawColor(if (tile.night) WALLPAPER_NIGHT else WALLPAPER_DAY)
            canvas.translate(margin.toFloat(), margin.toFloat())
            drawTree(view, canvas)
        }
        return bitmap
    }

    /**
     * Draws the view tree by hand into [canvas]. A plain `View.draw` ignores outline clipping, which is how the widget's
     * rounded corners are made, and the emulator's hardware renderer cannot be used headless, so each view's rounded
     * outline is applied as a clip path here.
     */
    private fun drawTree(view: View, canvas: Canvas) {
        canvas.save()
        canvas.translate(view.left.toFloat(), view.top.toFloat())
        val outline = Outline()
        view.outlineProvider?.getOutline(view, outline)
        val rect = Rect()
        if (view.clipToOutline && outline.getRect(rect) && outline.radius > 0f) {
            canvas.clipPath(Path().apply { addRoundRect(RectF(rect), outline.radius, outline.radius, Path.Direction.CW) })
        }
        if (view is ViewGroup) {
            // A view sets its background's bounds lazily, inside View.draw, which this walk bypasses.
            view.background?.apply {
                setBounds(0, 0, view.width, view.height)
                draw(canvas)
            }
            for (index in 0 until view.childCount) drawTree(view.getChildAt(index), canvas)
        } else {
            view.draw(canvas)
        }
        canvas.restore()
    }

    private companion object {
        const val MARGIN_DP = 12
        val WALLPAPER_DAY = Color.rgb(0x9A, 0xA7, 0xA3)
        val WALLPAPER_NIGHT = Color.rgb(0x2B, 0x2F, 0x3A)
    }
}
