@file:Suppress("MagicNumber", "LongMethod", "TooManyFunctions", "LongParameterList")

package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.flow.first

/*
 * The arched window from the lo-fi design, drawn in the design's own units: everything is laid out for a window 230 wide
 * and 287.5 tall, then scaled by (width / 230). Names follow the design's CSS (.glass, .muntin, .sill, .plant, .jar,
 * .lantern) so the two can be compared side by side.
 */

private const val DESIGN_WIDTH = 230f
private const val HEIGHT_PER_WIDTH = 1.25f
private const val PERCENT = 100f
private const val FILL_MILLIS = 590
private val DefaultWidth = 230.dp

private const val FRAME = 7f
private const val SILL_HEIGHT = 16f
private const val GLASS_BOTTOM_INSET = 14f
private const val OBJECT_BOTTOM = 15f
private const val TWO_PI = (2.0 * PI).toFloat()

private const val DAY_MID_STOP = 0.48f
private const val NIGHT_MID_STOP = 0.52f

private val RoofPath by lazy {
    PathParser().parsePathString("M0 70V40h14v-8h12v14h10V26h8v10h12V44h14V30h10V18h8v14h10v10h12V34h10v-6h10v16h14V36h12v34z").toPath()
}
private val MuntinArch by lazy { PathParser().parsePathString("M0 100C0 40 40 0 100 0s100 40 100 100").toPath() }

/** Stars: x, y, radius in a 200x250 box. */
private val Stars =
    listOf(
        Triple(30f, 60f, 1.2f), Triple(62f, 28f, 1.6f), Triple(96f, 48f, 1f), Triple(130f, 22f, 1.4f), Triple(158f, 64f, 1.1f),
        Triple(176f, 38f, 1.6f), Triple(48f, 100f, 1f), Triple(118f, 86f, 1.2f), Triple(150f, 112f, 1f), Triple(82f, 74f, 1f),
        Triple(20f, 120f, 1.2f), Triple(104f, 14f, 1f),
    )

/** Lit windows in the skyline's 200x70 box. */
private val CityLights =
    listOf(
        40f to 30f, 46f to 38f, 9f to 46f, 84f to 24f, 90f to 40f, 108f to 36f, 122f to 22f,
        150f to 34f, 168f to 30f, 186f to 42f, 22f to 54f, 70f to 52f, 132f to 52f, 176f to 56f,
    )

/** A painted cloud: left and top as fractions of the glass, width in design units, opacity, drift period (s) and phase (s). */
private class Cloud(val left: Float, val top: Float, val width: Float, val alpha: Float, val period: Float, val phase: Float)

private val Clouds =
    listOf(
        Cloud(0.06f, 0.22f, 92f, 0.92f, 58f, 20f),
        Cloud(0.44f, 0.40f, 74f, 0.92f, 78f, 44f),
        Cloud(0.10f, 0.56f, 92f, 0.70f, 44f, 8f),
    )

private class Streak(val x: Float, val top: Float, val length: Float, val period: Float, val delay: Float, val alpha: Float)

private class Dew(val x: Float, val y: Float, val width: Float, val height: Float)

private class RainField(val streaks: List<Streak>, val dew: List<Dew>)

/** The design's seeded generator, so the same drops fall in the preview and the app. */
private fun rainField(count: Int, seed: Int): RainField {
    var s = seed
    fun next(): Float {
        s = (s * 9301 + 49297) % 233280
        return s / 233280f
    }
    val streaks = List(count) {
        val x = next()
        val top = next() * 0.9f
        val length = 20f + next() * 34f
        val period = 1.5f + next() * 1.8f
        val delay = next() * 3f
        Streak(x, top, length, period, delay, 0.7f + next() * 0.3f)
    }
    val dew = List(16) {
        val d = 4f + next() * 5f
        Dew(next() * 0.96f, next() * 0.92f, d, d * 1.3f)
    }
    return RainField(streaks, dew)
}

private val WindowRain = rainField(34, 7)

/** The fraction of the water to draw: [progress] clamped to 0..1, so overshooting the goal never overflows the jar. */
internal fun jarFraction(progress: Float): Float = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)

/** The whole percent announced to TalkBack. Not capped, so 120 percent of the goal is reported as such. */
internal fun jarPercent(progress: Float): Int = if (progress.isNaN()) 0 else (progress.coerceAtLeast(0f) * PERCENT).roundToInt()

/**
 * A water spirit keeps watch beside the jar. Change [sipEvent] after a confirmed log to play the landing drop,
 * ripples and hop; change [goalEvent] for a first goal crossing to release the fireflies. Zero and the event values
 * on first composition are silent. [progress] always drives the jar and lantern, even with motion disabled.
 *
 * TalkBack announces "N percent of goal" for the whole hero (the visible [content] is not read separately).
 */
@Composable
fun WindowHero(
    progress: Float,
    modifier: Modifier = Modifier,
    width: Dp = DefaultWidth,
    raining: Boolean = false,
    animated: Boolean = false,
    description: String =
        pluralStringResource(R.plurals.core_designsystem_progress_ring_description, jarPercent(progress), jarPercent(progress)),
    sipEvent: Long = 0L,
    goalEvent: Long = 0L,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val fraction = jarFraction(progress)
    val enabled = rememberCozyMotionEnabled(animated)
    val sip = rememberEventPhase(sipEvent, SIP_DURATION, enabled)
    val goal = rememberEventPhase(goalEvent, GOAL_DURATION, enabled)
    val spiritSip = rememberSpiritSipPhase(sipEvent, goalEvent, goal, enabled)
    val level = rememberJarLevel(fraction, sipEvent, enabled)
    val extras = LocalDhmoExtraColors.current
    val colors = MaterialTheme.colorScheme
    val time = rememberAmbientTime(enabled)
    val lit = fraction >= 1f
    val look = HeroLook(extras, colors.primary, colors.tertiary, colors.onSurfaceVariant, colors.surfaceVariant, colors.secondary)
    Column(
        modifier =
            modifier.clearAndSetSemantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val scene: @Composable (HeroLook) -> Unit = { palette ->
            Canvas(Modifier.width(width).height(width * HEIGHT_PER_WIDTH)) {
                drawHero(palette, raining, lit, level.value, time.value, sip.value, spiritSip.value, goal.value)
            }
        }
        if (enabled) {
            Crossfade(targetState = look, animationSpec = tween(520), label = "windowLight", content = scene)
        } else {
            scene(look)
        }
        content()
    }
}

private fun DrawScope.drawHero(
    look: HeroLook,
    raining: Boolean,
    lit: Boolean,
    level: Float,
    time: Float,
    sip: Float,
    spiritSip: Float,
    goal: Float,
) {
    val s = size.width / DESIGN_WIDTH
    drawWindow(look, s, raining && !lit, time)
    val warmth = if (lit) pulse(goal, 0f, 0.22f, 1f) else 0f
    if (warmth > 0f) {
        val source = Offset(0.65f * size.width, 0.73f * size.height)
        val radius = size.width * 1.05f
        drawCircle(Brush.radialGradient(listOf(look.x.glow, Color.Transparent), source, radius), radius, source, 0.48f * warmth)
    }
    drawSill(look, s)
    drawPlantOnSill(look, s, time)
    drawJarOnSill(look, s, level, sip, time)
    drawLanternOnSill(look, s, lit, time)
    scale(s, Offset.Zero) {
        translate(155f, 272f) {
            val goalSip = goal * GOAL_DURATION / SIP_DURATION
            val celebrating = lit && goalSip < 1f
            drawWaterSpirit(time, if (celebrating) goalSip else spiritSip, celebrating, look.x.isNight)
        }
        if (lit) {
            drawGoalFireflies(goal)
            translate(7f, -18f) {
                drawGoalFireflies(interval(goal, 0.08f, 1f))
            }
        }
    }
}

/** The theme colours the hero draws with. */
private data class HeroLook(
    val x: DhmoExtraColors,
    val primary: Color,
    val leaf: Color,
    val inkMuted: Color,
    val surface2: Color,
    val glowInk: Color,
)

/** A sip during the milestone waits for the goal hop to land, without delaying its pour or goal effects. */
@Composable
private fun rememberSpiritSipPhase(sipEvent: Long, goalEvent: Long, goal: State<Float>, enabled: Boolean): State<Float> {
    if (!enabled) return rememberUpdatedState(1f)
    val phase = remember { Animatable(1f) }
    var previousSip by remember { mutableLongStateOf(sipEvent) }
    var previousGoal by remember { mutableLongStateOf(goalEvent) }
    LaunchedEffect(sipEvent, goalEvent) {
        val newSip = sipEvent != 0L && sipEvent != previousSip
        val newGoal = goalEvent != 0L && goalEvent != previousGoal
        previousSip = sipEvent
        previousGoal = goalEvent
        if (newGoal) {
            phase.snapTo(1f)
        } else if (newSip) {
            snapshotFlow { goal.value * GOAL_DURATION >= SIP_DURATION }.first { it }
            phase.snapTo(0f)
            phase.animateTo(1f, tween(SIP_DURATION, easing = LinearEasing))
        }
    }
    return phase.asState()
}

/** Only an explicitly confirmed sip eases the level upward. Edits, restores and undo draw their actual level. */
@Composable
private fun rememberJarLevel(fraction: Float, event: Long, enabled: Boolean): State<Float> {
    if (!enabled) return rememberUpdatedState(fraction)
    val level = remember { Animatable(fraction) }
    var lastEvent by remember { mutableLongStateOf(event) }
    LaunchedEffect(fraction, event) {
        val newSip = event != 0L && event != lastEvent
        lastEvent = event
        if (newSip && fraction > level.value) {
            level.animateTo(fraction, tween(FILL_MILLIS, delayMillis = (SIP_DURATION * IMPACT).toInt()))
        } else {
            level.snapTo(fraction)
        }
    }
    return level.asState()
}

/** The glass outline at [inset] design units inside the frame: an elliptical arch on top, [corner] at the bottom. */
private fun glassPath(width: Float, bottom: Float, inset: Float, corner: Float): Path {
    val left = inset
    val right = width - inset
    val base = bottom - inset
    val rx = width / 2f - inset
    val ry = 0.375f * bottom - inset
    return Path().apply {
        moveTo(left, inset + ry)
        arcTo(Rect(left, inset, right, inset + 2f * ry), 180f, 180f, forceMoveTo = false)
        lineTo(right, base - corner)
        if (corner > 0f) arcTo(Rect(right - 2f * corner, base - 2f * corner, right, base), 0f, 90f, forceMoveTo = false)
        lineTo(left + corner, base)
        if (corner > 0f) arcTo(Rect(left, base - 2f * corner, left + 2f * corner, base), 90f, 90f, forceMoveTo = false)
        close()
    }
}

private fun DrawScope.drawWindow(look: HeroLook, s: Float, raining: Boolean, time: Float) {
    val x = look.x
    val glassBottom = size.height - GLASS_BOTTOM_INSET * s
    drawPath(glassPath(size.width, glassBottom, 0f, 6f * s), x.wood)
    val inner = glassPath(size.width, glassBottom, FRAME * s, 0f)
    val iw = size.width - 2f * FRAME * s
    val ih = glassBottom - 2f * FRAME * s
    clipPath(inner) {
        translate(FRAME * s, FRAME * s) {
            drawSkyLayers(x, iw, ih)
            if (x.isNight) drawStars(x, iw, ih)
            Clouds.forEach { drawCloud(x, it, s, iw, ih, time) }
            drawCity(x, iw, ih)
            if (raining) drawRain(x, s, iw, ih, time)
            drawShine(x, iw, ih)
            drawMuntins(x, s, iw, ih)
            drawRect(
                Brush.radialGradient(
                    0.72f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.28f),
                    center = Offset(iw / 2f, ih / 2f),
                    radius = hypot(iw, ih) / 2f,
                ),
                size = Size(iw, ih),
            )
        }
    }
}

private fun DrawScope.drawSkyLayers(x: DhmoExtraColors, iw: Float, ih: Float) {
    val mid = if (x.isNight) NIGHT_MID_STOP else DAY_MID_STOP
    drawRect(Brush.verticalGradient(0f to x.skyTop, mid to x.skyMiddle, 1f to x.skyBottom, startY = 0f, endY = ih), size = Size(iw, ih))
    val sunCenter = Offset(0.72f * iw, 0.82f * ih)
    drawRect(Brush.radialGradient(listOf(x.sun, Color.Transparent), sunCenter, 0.55f * hypot(0.72f * iw, 0.82f * ih)), size = Size(iw, ih))
    val hazeCenter = Offset(0.18f * iw, 0.08f * ih)
    drawRect(
        Brush.radialGradient(listOf(x.haze, Color.Transparent), hazeCenter, 0.55f * hypot(0.82f * iw, 0.92f * ih)),
        size = Size(iw, ih),
    )
}

private fun DrawScope.drawStars(x: DhmoExtraColors, iw: Float, ih: Float) {
    val k = max(iw / 200f, ih / 250f)
    val ox = (iw - 200f * k) / 2f
    val oy = (ih - 250f * k) / 2f
    Stars.forEach { (cx, cy, r) -> drawCircle(x.star, r * k, Offset(ox + cx * k, oy + cy * k)) }
}

private fun pingPong(t: Float): Float {
    val f = t - kotlin.math.floor(t)
    return if (f < 0.5f) f * 2f else (1f - f) * 2f
}

/** Four overlapping blobs with a lit top-left, like the design's `.cloud`, drifting between -40 and +70 design units. */
private fun DrawScope.drawCloud(x: DhmoExtraColors, cloud: Cloud, s: Float, iw: Float, ih: Float, time: Float) {
    val drift = -40f + 110f * pingPong((time + cloud.phase) / (2f * cloud.period))
    val originX = cloud.left * iw + drift * s
    val originY = cloud.top * ih
    val pieces =
        listOf(
            Rect(0f, 14f, 42f, 40f),
            Rect(18f, -1f, 58f, 33f),
            Rect(44f, 14f, 90f, 40f),
            Rect(6f, 24f, cloud.width - 6f, 40f),
        )
    pieces.forEachIndexed { index, r ->
        val w = (r.width) * s
        val h = (r.height) * s
        val topLeft = Offset(originX + r.left * s, originY + r.top * s)
        val brush =
            Brush.radialGradient(
                listOf(x.cloudHigh, x.cloudLow),
                Offset(topLeft.x + 0.4f * w, topLeft.y + 0.32f * h),
                hypot(0.6f * w, 0.68f * h),
            )
        if (index == 3) {
            drawRoundRect(brush, topLeft, Size(w, h), CornerRadius(12f * s), alpha = cloud.alpha)
        } else {
            drawOval(brush, topLeft, Size(w, h), alpha = cloud.alpha)
        }
    }
}

private fun DrawScope.drawCity(x: DhmoExtraColors, iw: Float, ih: Float) {
    val h = 0.36f * ih
    translate(0f, ih - h) {
        val sx = iw / 200f
        val sy = h / 70f
        drawPath(RoofPath.copy(sx, sy), x.skyline)
        if (x.isNight) CityLights.forEach { (lx, ly) -> drawRect(x.glow, Offset(lx * sx, ly * sy), Size(2.6f * sx, 3f * sy)) }
    }
}

private fun Path.copy(sx: Float, sy: Float): Path = Path().also {
    it.addPath(this)
    it.transform(Matrix().apply { scale(sx, sy) })
}

private fun DrawScope.drawRain(x: DhmoExtraColors, s: Float, iw: Float, ih: Float, time: Float) {
    WindowRain.streaks.forEach { d ->
        val length = d.length * s
        val top =
            if (time == 0f) d.top * ih else (-0.2f + 1.3f * (((time + d.delay) / d.period) % 1f)) * ih
        drawLine(
            Brush.verticalGradient(listOf(Color.Transparent, x.rain), startY = top, endY = top + length),
            Offset(d.x * iw, top),
            Offset(d.x * iw, top + length),
            strokeWidth = 2.2f * s,
            cap = StrokeCap.Round,
            alpha = d.alpha,
        )
    }
    WindowRain.dew.forEach { d ->
        val w = d.width * s
        val h = d.height * s
        val tl = Offset(d.x * iw, d.y * ih)
        drawOval(
            Brush.radialGradient(listOf(x.rain, Color.Transparent), Offset(tl.x + 0.35f * w, tl.y + 0.3f * h), max(w, h) * 0.8f),
            tl,
            Size(w, h),
            alpha = 0.8f,
        )
    }
}

private fun DrawScope.drawShine(x: DhmoExtraColors, iw: Float, ih: Float) {
    val angle = 115f * PI.toFloat() / 180f
    val dx = sin(angle)
    val dy = -cos(angle)
    val length = abs(iw * dx) + abs(ih * dy)
    val c = Offset(iw / 2f, ih / 2f)
    val start = Offset(c.x - dx * length / 2f, c.y - dy * length / 2f)
    val end = Offset(c.x + dx * length / 2f, c.y + dy * length / 2f)
    drawRect(
        Brush.linearGradient(
            0f to x.shine,
            0.24f to Color.Transparent,
            0.62f to Color.Transparent,
            0.78f to x.shine,
            0.84f to Color.Transparent,
            start = start,
            end = end,
        ),
        size = Size(iw, ih),
        alpha = 0.5f,
    )
}

private fun DrawScope.drawMuntins(x: DhmoExtraColors, s: Float, iw: Float, ih: Float) {
    val stroke = Stroke(width = 4f * s)
    drawLine(x.wood, Offset(iw / 2f, 0f), Offset(iw / 2f, ih), strokeWidth = stroke.width)
    drawLine(x.wood, Offset(0f, 120f / 250f * ih), Offset(iw, 120f / 250f * ih), strokeWidth = stroke.width)
    drawPath(MuntinArch.copy(iw / 200f, ih / 250f), x.wood, style = stroke)
}

private fun DrawScope.drawSill(look: HeroLook, s: Float) {
    val top = size.height - SILL_HEIGHT * s
    drawRoundRect(
        Color.Black.copy(alpha = 0.18f),
        Offset(-8f * s + 2f * s, top + 5f * s),
        Size(size.width + 16f * s - 4f * s, SILL_HEIGHT * s),
        CornerRadius(5f * s),
    )
    drawRoundRect(
        Brush.verticalGradient(listOf(look.x.woodHighlight, look.x.wood), startY = top, endY = size.height),
        Offset(-8f * s, top),
        Size(size.width + 16f * s, SILL_HEIGHT * s),
        CornerRadius(5f * s),
    )
}

private fun DrawScope.drawPlantOnSill(look: HeroLook, s: Float, time: Float) {
    drawPlant(
        Offset(0.07f * size.width, size.height - (OBJECT_BOTTOM + 56f) * s),
        38f * s,
        look.leaf,
        look.x.wood,
        look.x.woodHighlight,
        sway = sin(time * 1.08f) * 2.8f,
    )
}

private fun DrawScope.drawJarOnSill(look: HeroLook, s: Float, fill: Float, sip: Float, time: Float) {
    val x = look.x
    val cx = 102f * s
    val bottom = size.height - OBJECT_BOTTOM * s
    val bodyW = 56f * s
    val bodyH = 68f * s
    val bodyTop = bottom - bodyH
    val left = cx - bodyW / 2f
    val afterImpact = interval(sip, IMPACT, 1f)
    val tilt = -2.7f * sin(afterImpact * TWO_PI * 1.6f) * (1f - afterImpact) * (1f - afterImpact)
    rotate(tilt, Offset(cx, bottom)) {
        val body =
            RoundRect(
                left,
                bodyTop,
                left + bodyW,
                bottom,
                CornerRadius(10f * s),
                CornerRadius(10f * s),
                CornerRadius(14f * s),
                CornerRadius(14f * s),
            )
        val bodyPath = Path().apply { addRoundRect(body) }
        drawPath(bodyPath, x.glass)
        val insideLeft = left + 2f * s
        val insideTop = bodyTop + 2f * s
        val insideW = bodyW - 4f * s
        val insideH = bodyH - 4f * s
        clipPath(bodyPath) {
            val water = insideH * fill
            if (fill > 0f) {
                val surface = bottom - 2f * s - water
                drawRect(
                    Brush.verticalGradient(listOf(x.water, look.primary), startY = surface, endY = bottom),
                    Offset(insideLeft, surface),
                    Size(insideW, water + 2f * s),
                    alpha = 0.92f,
                )
                val slosh = sin(time * TWO_PI / 3.8f) * 1.4f * s
                val wave = sin(afterImpact * TWO_PI * 2f) * (1f - afterImpact) * 4f * s
                drawOval(x.water, Offset(insideLeft - 0.1f * insideW + slosh, surface - 3f * s + wave), Size(1.2f * insideW, 7f * s))
                drawSipRings(cx, surface, s, afterImpact)
            }
            drawRoundRect(x.shine, Offset(insideLeft + 6f * s, insideTop + 6f * s), Size(5f * s, insideH - 16f * s), CornerRadius(4f * s))
        }
        drawPath(bodyPath, look.inkMuted, style = Stroke(width = 2f * s))
        val lidW = 34f * s
        val lidH = 8f * s
        val lift = pulse(sip, 0f, 0.23f, 0.73f)
        translate(-23f * s * lift, -8f * s * lift) {
            rotate(-17f * lift, Offset(cx, bodyTop)) {
                drawRoundRect(x.wood, Offset(cx - lidW / 2f, bodyTop - lidH), Size(lidW, lidH), CornerRadius(3f * s))
                drawRect(x.woodHighlight, Offset(cx - lidW / 2f, bodyTop - lidH), Size(lidW, s))
            }
        }
    }
    drawPitcherPour(look, s, cx, bodyTop, sip)
    if (afterImpact > 0f && afterImpact < 0.42f) {
        val t = afterImpact / 0.42f
        for (side in -1..1 step 2) {
            val drop = Offset(cx + side * (4f + t * 13f) * s, bodyTop - (7f + 11f * 4f * t * (1f - t)) * s)
            drawCircle(x.water, (1.9f - 0.8f * t) * s, drop, alpha = pulse(t, 0f, 0.12f, 1f))
        }
    }
}

private val PitcherBody = PathParser().parsePathString(
    "M-28-22 Q-30-26-25-27 L-9-27 Q-6-26-5-22 L0-20 L-6-17 L-8-3 Q-9 1-15 1 L-27 1 Q-33 0-33-6 Z",
).toPath()
private val PitcherHandle = PathParser().parsePathString("M-29-22 C-42-26-42-7-32-7").toPath()

/** The spout is the pivot and stream origin, so the stream stays attached throughout the pour. */
private fun DrawScope.drawPitcherPour(look: HeroLook, s: Float, cx: Float, bodyTop: Float, sip: Float) {
    val visibility = pulse(sip, 0f, 0.12f, 0.92f)
    if (visibility <= 0f) return
    val spout = Offset(cx - 17f * s, bodyTop - 45f * s)
    val tilt = 28f * pulse(sip, 0.08f, 0.27f, 0.78f)
    translate(spout.x, spout.y) {
        scale(s, s, Offset.Zero) {
            rotate(tilt, Offset.Zero) {
                translate(0f, 20f) {
                    drawPath(PitcherHandle, look.x.wood.copy(alpha = visibility), style = Stroke(3f, cap = StrokeCap.Round))
                    drawPath(PitcherBody, look.x.woodHighlight.copy(alpha = visibility))
                    drawPath(PitcherBody, look.inkMuted.copy(alpha = visibility), style = Stroke(1.5f))
                    drawLine(Color.White.copy(alpha = 0.45f * visibility), Offset(-27f, -19f), Offset(-27f, -7f), 1.5f, StrokeCap.Round)
                }
            }
        }
    }
    val stream = pulse(sip, 0.16f, 0.30f, 0.76f)
    if (stream > 0f) {
        val length = smoothStep(interval(sip, 0.16f, IMPACT))
        var previous = spout
        // A quadratic arc sampled along its length; every segment shares its neighbour's endpoint.
        for (segment in 1..12) {
            val t = length * segment / 12f
            val point = Offset(
                spout.x + 17f * s * t,
                spout.y + 45f * s * t * t,
            )
            drawLine(look.x.water.copy(alpha = 0.88f * stream), previous, point, 3f * s, StrokeCap.Round)
            previous = point
        }
    }
}

private fun DrawScope.drawSipRings(cx: Float, surface: Float, s: Float, afterImpact: Float) {
    repeat(2) { ring ->
        val t = interval(afterImpact, ring * 0.16f, 0.73f + ring * 0.16f)
        if (t > 0f && t < 1f) {
            val radius = (2f + 23f * (1f - (1f - t) * (1f - t))) * s
            val depth = radius * 0.19f
            drawOval(
                Color(0xFFEBF2D9),
                Offset(cx - radius, surface - depth + 1.5f * s),
                Size(radius * 2f, depth * 2f),
                alpha = 0.84f * pulse(t, 0f, 0.12f, 1f),
                style = Stroke(1.15f * s),
            )
        }
    }
}

private fun DrawScope.drawLanternOnSill(look: HeroLook, s: Float, lit: Boolean, time: Float) {
    val w = 34f * s
    val h = 48f * s
    val left = size.width - 0.08f * size.width - w
    val top = size.height - OBJECT_BOTTOM * s - h
    if (lit) {
        val pulse = if (time == 0f) 1f else 1f - 0.25f * ((1f - cos(time * TWO_PI / 4.5f)) / 2f)
        val radius = 70f * s * (if (time == 0f) 1f else 1f - 0.07f * ((1f - cos(time * TWO_PI / 4.5f)) / 2f))
        val center = Offset(left + w / 2f, top + 0.48f * h)
        drawCircle(
            Brush.radialGradient(0f to look.x.glowHalo, 0.65f to Color.Transparent, center = center, radius = radius),
            radius,
            center,
            alpha = pulse,
        )
    }
    drawLantern(Offset(left, top), w, if (lit) look.x.glow else look.surface2, if (lit) look.glowInk else look.inkMuted)
}
