@file:Suppress("MagicNumber")

package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import casa.peterson.dhmo.core.designsystem.theme.LocalDhmoExtraColors
import kotlin.math.sin

private val LeafLeft = PathParser().parsePathString("M0 0C-9 2-16-4-16-13C-6-14-1-8 0 0Z").toPath()
private val LeafRight = PathParser().parsePathString("M0 0C0-9 7-15 16-15C16-6 10 0 0 0Z").toPath()
private val Petal = PathParser().parsePathString("M0 1C-4-1-6-7-3-9C1-12 5-7 4-4Q3 0 0 1Z").toPath()
private val GardenMoss = Color(0xFF789567)
private val GardenSage = Color(0xFFA6B98B)
private val StemInk = Color(0xFF536F51)
private val Soil = Color(0xFF8A735B)
private const val OPEN_FLOWER = 1.24f

/**
 * Seven equal plots in reading order. The caller owns the weekday labels and accessible day values.
 * A positive fraction is a growing plant, a full goal adds a flower, and a day without entries is quiet soil.
 */
@Composable
fun WeeklyGarden(growth: List<Float>, modifier: Modifier = Modifier, animated: Boolean = true) {
    val enabled = rememberCozyMotionEnabled(animated)
    val time by rememberAmbientTime(enabled)
    val night = LocalDhmoExtraColors.current.isNight
    val plants =
        List(7) { day ->
            key(day) {
                val fraction = jarFraction(growth.getOrNull(day) ?: 0f)
                val target = if (fraction == 1f) OPEN_FLOWER else fraction
                if (enabled) animateFloatAsState(target, tween(820), label = "plantGrowth") else rememberUpdatedState(target)
            }
        }
    Canvas(modifier.fillMaxWidth().height(112.dp)) {
        val s = minOf(size.height / 102f, size.width / 255f)
        val horizon = size.height - 13f * s
        val plotWidth = size.width / 7f
        drawGardenSky(night, s, horizon)
        plants.forEachIndexed { day, plant ->
            val position = if (layoutDirection == LayoutDirection.Rtl) 6 - day else day
            translate(plotWidth * (position + 0.5f), horizon) {
                scale(s, Offset.Zero) { drawPlot(day, plant.value, time, night) }
            }
        }
    }
}

private fun DrawScope.drawGardenSky(night: Boolean, s: Float, horizon: Float) {
    val sun = Offset(size.width * 0.81f, size.height * 0.24f)
    val wash = if (night) Color(0xFFF4CA88) else Color(0xFFE1B668)
    drawCircle(Brush.radialGradient(listOf(wash.copy(alpha = 0.24f), Color.Transparent), sun, 39f * s), 39f * s, sun)
    drawCircle(wash.copy(alpha = 0.66f), 9f * s, sun)
    val ridge = Path().apply {
        moveTo(0f, horizon - 8f * s)
        cubicTo(size.width * 0.29f, horizon - 39f * s, size.width * 0.62f, horizon + 1f * s, size.width, horizon - 24f * s)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(ridge, GardenMoss.copy(alpha = 0.13f))
    drawRect(Soil.copy(alpha = 0.10f), Offset(0f, horizon + 1f * s), Size(size.width, size.height - horizon - s))
}

private fun DrawScope.drawPlot(day: Int, growth: Float, time: Float, night: Boolean) {
    val patch = if (night) Color(0xFFAC9073) else Soil
    drawOval(patch.copy(alpha = 0.26f), Offset(-14f, -1.9f), Size(28f, 4.5f))
    drawOval(patch.copy(alpha = 0.47f), Offset(-10f, -2.8f), Size(4.5f, 1.9f))
    drawCircle(patch.copy(alpha = 0.49f), 1.0f, Offset(9f, 0.1f))
    drawCircle(patch.copy(alpha = 0.32f), 0.75f, Offset(5.2f, 2.7f))
    if (growth <= 0f) return
    val height = growth.coerceAtMost(1f)
    val h = 13f + 55f * height
    val breeze = sin(time * 1.2f + day * 0.68f) - sin(day * 0.68f)
    val lean = (day % 3 - 1) * 3.2f + 1.7f * breeze
    rotate(lean, Offset.Zero) {
        val stem = Path().apply {
            moveTo(0f, 0f)
            cubicTo(1.8f, -h * 0.28f, -1.8f, -h * 0.62f, 0f, -h)
        }
        drawPath(stem, if (night) GardenSage else StemInk, style = Stroke(1.85f, cap = StrokeCap.Round))
        val spread = 0.4f + 0.62f * height
        drawGardenLeaf(-h * 0.31f, left = true, spread)
        drawGardenLeaf(-h * 0.57f, left = false, spread * 0.97f)
        val upper = interval(growth, 0.22f, 0.57f)
        if (upper > 0f) drawGardenLeaf(-h * 0.74f, left = true, spread * 0.8f * upper)
        // A bud stays closed until this day actually reaches the goal.
        drawOval(GardenSage, Offset(-2.3f, -h - 4f), Size(4.6f, 6f))
        val bloom = smoothStep(interval(growth, 1f, OPEN_FLOWER))
        if (bloom > 0f) {
            translate(0f, -h - 2f) {
                rotate(-12f * (1f - bloom), Offset.Zero) {
                    scale(bloom, Offset.Zero) { drawBlossom(day) }
                }
            }
        }
    }
}

private fun DrawScope.drawGardenLeaf(y: Float, left: Boolean, spread: Float) {
    translate(0f, y) {
        scale(spread, Offset.Zero) {
            val leaf = if (left) LeafLeft else LeafRight
            drawPath(leaf, if (left) GardenSage else GardenMoss)
            drawPath(leaf, StemInk.copy(alpha = 0.78f), style = Stroke(0.75f))
            drawLine(StemInk.copy(alpha = 0.65f), Offset.Zero, Offset(if (left) -11f else 11f, -9f), 0.72f, StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawBlossom(day: Int) {
    val light = if (day % 3 == 0) Color(0xFFF1CEBE) else Color(0xFFF0D89D)
    val shade = if (day % 3 == 0) Color(0xFFCE9C8B) else Color(0xFFD6B177)
    repeat(5) { petal ->
        rotate(72f * petal + 9f * day, Offset.Zero) {
            drawPath(Petal, if (petal % 2 == 0) light else shade)
            drawPath(Petal, shade, style = Stroke(0.7f))
        }
    }
    drawCircle(Soil, 2.8f, Offset.Zero)
    drawCircle(Color(0xFFFFE4A6), 1.15f, Offset(-0.75f, -0.65f))
}
