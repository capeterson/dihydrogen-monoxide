package casa.peterson.dhmo.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Composes every layout in every state, in day and night, into real RemoteViews, inflates them at the cell size the
 * launcher would give, and draws them. Fails if any of that throws or a layout comes out empty.
 *
 * With `WIDGET_SCREENSHOT_DIR` set it also writes each drawing there as `<sheet>__<order>__<label>.png`, which is how the
 * pull request's screenshots are made (see `docs/TESTING.md`).
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xxhdpi")
class WidgetRenderTest {
    private data class Tile(
        val sheet: String,
        val label: String,
        val sample: WidgetSample,
        val size: DpSize,
        val night: Boolean = false,
        val units: VolumeUnit = VolumeUnit.ML,
        val fontScale: Float = 1f,
    )

    @Test
    fun `every layout and state renders`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val out = System.getenv("WIDGET_SCREENSHOT_DIR")?.let { File(it).apply { mkdirs() } }
        tiles().forEachIndexed { index, tile ->
            val bitmap = render(context, tile)
            out?.let { dir ->
                val name = "${tile.sheet}__${index.toString().padStart(3, '0')}__${tile.label}.png"
                File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    /**
     * With `WIDGET_PREVIEW_DIR` set, draws the four picker entries as the widget picker shows them below Android 15 (where
     * generated previews are not available): on a transparent background, an on-track afternoon. The output is converted
     * to `widget/src/main/res/drawable-nodpi/widget_preview_*.webp`.
     */
    @Test
    fun `picker previews render`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val out = System.getenv("WIDGET_PREVIEW_DIR")?.let { File(it).apply { mkdirs() } }
        mapOf(
            "wide_strip" to Cells.getValue("3x1 wide strip"),
            "tall_strip" to Cells.getValue("1x3 tall strip"),
            "wide_card" to Cells.getValue("4x2 wide card"),
            "tall_card" to Cells.getValue("2x4 tall card"),
        ).forEach { (name, size) ->
            val bitmap = render(context, Tile("preview", name, WidgetSample.ON_TRACK, size), wallpaper = false)
            out?.let { dir ->
                File(dir, "widget_preview_$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    private fun tiles(): List<Tile> = buildList {
        for (night in listOf(false, true)) {
            val sheet = if (night) "layouts-night" else "layouts-day"
            Cells.forEach { (name, size) -> add(Tile(sheet, name, WidgetSample.BEHIND, size, night)) }
        }
        val states = listOf(
            "on track" to WidgetSample.ON_TRACK,
            "goal reached" to WidgetSample.GOAL_REACHED,
            "just added, undo" to WidgetSample.JUST_ADDED,
            "no goal yet" to WidgetSample.NO_GOAL,
            "before the wake window" to WidgetSample.OUTSIDE_WAKE_WINDOW,
            "two containers" to WidgetSample.FEW_CONTAINERS,
            "no containers" to WidgetSample.NO_CONTAINERS,
            "setup not finished" to WidgetSample.ONBOARDING,
        )
        for ((state, sample) in states) {
            for (night in listOf(false, true)) {
                val suffix = if (night) ", night" else ""
                add(Tile("states-cards", "wide card, $state$suffix", sample, Cells.getValue("4x2 wide card"), night))
                add(Tile("states-cards", "tall card, $state$suffix", sample, Cells.getValue("2x4 tall card"), night))
                add(Tile("states-strips", "wide strip, $state$suffix", sample, Cells.getValue("3x1 wide strip"), night))
                add(Tile("states-strips", "tall strip, $state$suffix", sample, Cells.getValue("1x3 tall strip"), night))
                add(Tile("states-small", "compact, $state$suffix", sample, Cells.getValue("2x1 compact"), night))
                add(Tile("states-small", "square, $state$suffix", sample, Cells.getValue("2x2 square"), night))
            }
        }
        LandscapeCells.forEach { (name, size) -> add(Tile("layouts-landscape", "$name, landscape", WidgetSample.BEHIND, size)) }
        // The system's largest font size: the widget's text grows to its cap and still fits.
        Cells.forEach { (name, size) -> add(Tile("font-200", "$name, font 200%", WidgetSample.BEHIND, size, fontScale = 2f)) }
        add(
            Tile(
                "font-200",
                "tall strip, goal reached, font 200%",
                WidgetSample.GOAL_REACHED,
                Cells.getValue("1x3 tall strip"),
                fontScale = 2f,
            ),
        )
        add(Tile("font-200", "wide card, undo, font 200%", WidgetSample.JUST_ADDED, Cells.getValue("4x2 wide card"), fontScale = 2f))
        Cells.forEach { (name, size) -> add(Tile("units", "$name, US fl oz", WidgetSample.BEHIND, size, units = VolumeUnit.US_FL_OZ)) }
        Cells.forEach { (name, size) -> add(Tile("units", "$name, litres", WidgetSample.BEHIND, size, units = VolumeUnit.L)) }
    }

    private fun render(base: Context, tile: Tile, wallpaper: Boolean = true): Bitmap {
        val context = if (tile.fontScale == 1f) {
            base
        } else {
            base.createConfigurationContext(Configuration(base.resources.configuration).apply { fontScale = tile.fontScale })
        }
        val model = sampleModel(context.resources, tile.sample, tile.units, Locale.US, tile.night)
        val art = WidgetArt.render(model, context.resources.displayMetrics.density)
        // Compose at the breakpoint the launcher would choose, then lay it out at the real cell size, as a launcher does.
        val breakpoint = selectBreakpoint(tile.size)
        val views = runBlocking {
            GlanceRemoteViews().compose(context, breakpoint.size, null, Bundle()) { WidgetRoot(model, art) }.remoteViews
        }
        val density = context.resources.displayMetrics.density
        val width = (tile.size.width.value * density).roundToInt()
        val height = (tile.size.height.value * density).roundToInt()
        val view: View = views.apply(context, FrameLayout(context))
        view.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        assertThat(view.width).isGreaterThan(0)

        val margin = if (wallpaper) (MARGIN_DP * density).roundToInt() else 0
        val bitmap = Bitmap.createBitmap(width + 2 * margin, height + 2 * margin, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (wallpaper) canvas.drawColor(if (tile.night) WALLPAPER_NIGHT else WALLPAPER_DAY)
        canvas.translate(margin.toFloat(), margin.toFloat())
        // The launcher clips the widget to its corner radius; drawing by hand skips that, so clip here.
        val radius = CORNER_DP * density
        canvas.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, Path.Direction.CW) })
        view.draw(canvas)
        return bitmap
    }

    private companion object {
        const val MARGIN_DP = 14
        const val CORNER_DP = 20f
        val WALLPAPER_DAY = Color.rgb(0x8E, 0x9E, 0x98)
        val WALLPAPER_NIGHT = Color.rgb(0x2B, 0x2F, 0x3A)

        /** Portrait cells on a typical phone, from Android's widget sizing guidance (73n - 16 wide, 118n - 16 tall). */
        val Cells = linkedMapOf(
            "2x1 compact" to DpSize(130.dp, 102.dp),
            "3x1 wide strip" to DpSize(203.dp, 102.dp),
            "1x3 tall strip" to DpSize(57.dp, 338.dp),
            "2x2 square" to DpSize(130.dp, 220.dp),
            "4x2 wide card" to DpSize(276.dp, 220.dp),
            "2x4 tall card" to DpSize(130.dp, 455.dp),
        )

        /** Landscape cells (142n - 15 wide, 66n - 15 tall): short and wide. */
        val LandscapeCells = linkedMapOf(
            "2x1 compact" to DpSize(269.dp, 51.dp),
            "3x1 wide strip" to DpSize(412.dp, 51.dp),
            "1x3 tall strip" to DpSize(127.dp, 184.dp),
            "2x2 square" to DpSize(269.dp, 117.dp),
            "4x2 wide card" to DpSize(554.dp, 117.dp),
            "2x4 tall card" to DpSize(269.dp, 250.dp),
        )
    }
}
