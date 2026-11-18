package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import casa.peterson.dhmo.core.designsystem.R
import casa.peterson.dhmo.core.designsystem.theme.DhmoExtraColors
import casa.peterson.dhmo.core.designsystem.theme.LocalDhmoExtraColors
import kotlin.math.roundToInt

private const val PERCENT = 100f
private const val FILL_MILLIS = 700
private const val HEIGHT_PER_WIDTH = 1.18f
private const val SILL_FRACTION = 0.075f
private const val HALF = 0.5f
private const val START_ANGLE = 180f
private const val SWEEP_ANGLE = 180f
private val FrameWidth = 5.dp
private val JarStrokeWidth = 2.dp
private val DefaultWidth = 240.dp

private const val JAR_WIDTH = 0.36f
private const val JAR_HEIGHT = 0.42f
private const val JAR_CORNER = 0.14f
private const val LID_WIDTH = 0.7f
private const val LID_HEIGHT = 0.07f
private const val WATER_INSET = 0.06f
private const val GLASS_STROKE_ALPHA = 0.55f
private const val MENISCUS_ALPHA = 0.5f

private const val LANTERN_X = 0.8f
private const val LANTERN_RADIUS = 0.07f
private const val LANTERN_GLOW_RADIUS = 0.3f
private const val LANTERN_UNLIT_ALPHA = 0.35f

private const val STREAK_COUNT = 16
private const val STREAK_STEP = 0.067f
private const val STREAK_SLANT = 0.03f
private const val STREAK_LENGTH = 0.09f
private const val STREAK_MIN_FACTOR = 0.6f

/** Star positions as fractions of the window's width and height, kept still so the preview and tests are stable. */
private val StarPositions =
    listOf(
        0.18f to 0.14f, 0.34f to 0.08f, 0.52f to 0.18f, 0.7f to 0.1f, 0.84f to 0.22f,
        0.26f to 0.3f, 0.62f to 0.32f, 0.9f to 0.4f, 0.1f to 0.42f,
    )

/** Cloud puffs: centre x, centre y, radius, each a fraction of the window width. */
private val CloudPuffs =
    listOf(
        Triple(0.26f, 0.36f, 0.09f),
        Triple(0.34f, 0.33f, 0.11f),
        Triple(0.43f, 0.37f, 0.08f),
        Triple(0.68f, 0.22f, 0.07f),
        Triple(0.75f, 0.2f, 0.09f),
        Triple(0.83f, 0.23f, 0.06f),
    )

/** City lights along the horizon at night: x fraction and light width fraction. */
private val CityLights =
    listOf(0.08f to 0.03f, 0.2f to 0.02f, 0.31f to 0.035f, 0.46f to 0.02f, 0.58f to 0.03f, 0.72f to 0.025f, 0.86f to 0.03f)

private const val CITY_HEIGHT = 0.12f
private const val CITY_LIGHT_SIZE = 0.012f

/** The fraction of the water to draw: [progress] clamped to 0..1, so overshooting the goal never overflows the jar. */
internal fun jarFraction(progress: Float): Float = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)

/** The whole percent announced to TalkBack. Not capped, so 120 percent of the goal is reported as such. */
internal fun jarPercent(progress: Float): Int = if (progress.isNaN()) 0 else (progress.coerceAtLeast(0f) * PERCENT).roundToInt()

/**
 * Today's hero: an arched window with a glass jar on the sill. The jar fills as [progress] (a fraction of the goal,
 * 1.0 is the goal) grows and a paper lantern lights on the sill when it is reached. The sky is the day or night sky of
 * the theme; [raining] streaks the glass when the user is behind pace. [content] is laid out under the sill.
 *
 * TalkBack announces "N percent of goal" for the whole hero (the visible [content] is not read separately).
 */
@Composable
fun WindowHero(
    progress: Float,
    modifier: Modifier = Modifier,
    width: Dp = DefaultWidth,
    raining: Boolean = false,
    description: String =
        pluralStringResource(R.plurals.core_designsystem_progress_ring_description, jarPercent(progress), jarPercent(progress)),
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val fraction = jarFraction(progress)
    val animated by animateFloatAsState(fraction, tween(FILL_MILLIS), label = "jarFill")
    val extras = LocalDhmoExtraColors.current
    val jarOutline = MaterialTheme.colorScheme.onSurface.copy(alpha = GLASS_STROKE_ALPHA)
    Column(
        modifier =
            modifier.clearAndSetSemantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.width(width).height(width * HEIGHT_PER_WIDTH)) {
            val sillTop = size.height * (1f - SILL_FRACTION)
            val arch = archPath(size.width, sillTop)
            clipPath(arch) {
                drawSky(extras, sillTop)
                if (raining && fraction < 1f) drawRain(extras.rain, sillTop)
            }
            drawPath(arch, extras.wood, style = Stroke(width = FrameWidth.toPx()))
            drawSill(extras, sillTop)
            drawJar(extras, animated, sillTop, jarOutline)
            drawLantern(extras, lit = fraction >= 1f, sillTop = sillTop)
        }
        content()
    }
}

private fun archPath(width: Float, bottom: Float): Path = Path().apply {
    moveTo(0f, bottom)
    lineTo(0f, width * HALF)
    arcTo(Rect(0f, 0f, width, width), START_ANGLE, SWEEP_ANGLE, forceMoveTo = false)
    lineTo(width, bottom)
    close()
}

private fun DrawScope.drawSky(extras: DhmoExtraColors, horizon: Float) {
    drawRect(Brush.verticalGradient(listOf(extras.skyTop, extras.skyMiddle, extras.skyBottom), endY = horizon))
    if (extras.isNight) {
        StarPositions.forEach { (x, y) ->
            drawCircle(extras.star, radius = size.width * CITY_LIGHT_SIZE, center = Offset(size.width * x, horizon * y))
        }
    }
    CloudPuffs.forEach { (x, y, r) ->
        drawCircle(extras.cloud, radius = size.width * r, center = Offset(size.width * x, horizon * y))
    }
    if (extras.isNight) {
        drawRect(extras.skyline, topLeft = Offset(0f, horizon * (1f - CITY_HEIGHT)), size = Size(size.width, horizon * CITY_HEIGHT))
        CityLights.forEach { (x, w) ->
            drawRect(
                extras.glow,
                topLeft = Offset(size.width * x, horizon * (1f - CITY_HEIGHT * HALF)),
                size = Size(size.width * w, size.width * CITY_LIGHT_SIZE),
            )
        }
    }
}

private fun DrawScope.drawRain(color: Color, horizon: Float) {
    val stroke = JarStrokeWidth.toPx() * HALF
    repeat(STREAK_COUNT) { i ->
        val x = size.width * (STREAK_STEP * (i + 1))
        val y = horizon * (((i * 7) % STREAK_COUNT) / STREAK_COUNT.toFloat())
        val length = size.width * STREAK_LENGTH * (STREAK_MIN_FACTOR + (i % 3) * STREAK_MIN_FACTOR * HALF)
        drawLine(color, Offset(x, y), Offset(x - size.width * STREAK_SLANT, y + length), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawSill(extras: DhmoExtraColors, top: Float) {
    val overhang = FrameWidth.toPx()
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(extras.woodHighlight, extras.wood), startY = top, endY = size.height),
        topLeft = Offset(-overhang, top),
        size = Size(size.width + overhang * 2, size.height - top),
        cornerRadius = CornerRadius(overhang),
    )
}

private fun DrawScope.drawJar(extras: DhmoExtraColors, fill: Float, sillTop: Float, outline: Color) {
    val jarWidth = size.width * JAR_WIDTH
    val jarHeight = size.height * JAR_HEIGHT
    val left = (size.width - jarWidth) * HALF
    val top = sillTop - jarHeight
    val body = RoundRect(left, top, left + jarWidth, sillTop, CornerRadius(jarWidth * JAR_CORNER))
    val bodyPath = Path().apply { addRoundRect(body) }
    drawPath(bodyPath, extras.glass)
    clipPath(bodyPath) {
        val inset = jarHeight * WATER_INSET
        val surface = sillTop - (jarHeight - inset) * fill
        if (fill > 0f) {
            drawRect(extras.water, topLeft = Offset(left, surface), size = Size(jarWidth, sillTop - surface))
            drawLine(
                Color.White.copy(alpha = MENISCUS_ALPHA),
                Offset(left, surface),
                Offset(left + jarWidth, surface),
                strokeWidth = JarStrokeWidth.toPx(),
            )
        }
    }
    drawPath(bodyPath, outline, style = Stroke(width = JarStrokeWidth.toPx()))
    val lidWidth = jarWidth * LID_WIDTH
    drawRoundRect(
        outline,
        topLeft = Offset((size.width - lidWidth) * HALF, top - jarHeight * LID_HEIGHT),
        size = Size(lidWidth, jarHeight * LID_HEIGHT),
        cornerRadius = CornerRadius(JarStrokeWidth.toPx()),
    )
}

private fun DrawScope.drawLantern(extras: DhmoExtraColors, lit: Boolean, sillTop: Float) {
    val radius = size.width * LANTERN_RADIUS
    val center = Offset(size.width * LANTERN_X, sillTop - radius)
    if (lit) {
        drawCircle(
            Brush.radialGradient(listOf(extras.glow.copy(alpha = HALF), Color.Transparent), center, size.width * LANTERN_GLOW_RADIUS),
            radius = size.width * LANTERN_GLOW_RADIUS,
            center = center,
        )
        drawCircle(extras.glow, radius, center)
    } else {
        drawCircle(extras.glow.copy(alpha = LANTERN_UNLIT_ALPHA), radius, center)
    }
    drawCircle(extras.wood, radius, center, style = Stroke(width = JarStrokeWidth.toPx()))
}
