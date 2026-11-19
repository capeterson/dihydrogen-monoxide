@file:Suppress("MagicNumber")

package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import casa.peterson.dhmo.core.designsystem.theme.LocalDhmoExtraColors
import kotlin.math.cos
import kotlin.math.sin

// A cel-painted droplet, drawn around its feet so every anticipation and landing stays grounded.
private fun spiritPath(d: String) = PathParser().parsePathString(d).toPath()

private val Body = spiritPath("M-1-43C-4-34-18-28-20-17C-22-6-14 0-2 0C11 1 20-4 20-14C21-24 8-34-1-43Z")
private val BodyShade = spiritPath("M-19-12C-13-5 2-3 11-9C16-12 17-19 15-25C19-20 21-16 19-11C17-2 7 0-2 0C-12 0-18-4-19-12Z")
private val RimLight = spiritPath("M-15-21Q-12-27-8-30")
private val Leaf = spiritPath("M-3-36C-9-48 2-54 18-52C17-44 12-38 5-36Q0-34-3-36Z")
private val LeafLight = spiritPath("M-3-36C-8-46 3-52 16-51Q4-48-3-36Z")
private val Veins = spiritPath("M-4-35Q3-45 14-49M2-42l1-5M7-45l5 0")
private val HappyEye = spiritPath("M-3 0Q0-4.3 3 0")
private val Smile = spiritPath("M-3-11Q0-8 3-11")
private val Belly = spiritPath("M-2-5Q1-4 4-5")
private val Ink = Color(0xFF34594F)
private val Moss = Color(0xFF769461)
private val LeafInk = Color(0xFF48674B)
private val Blush = Color(0xFFDEA893)
private val CheekLight = Color(0xFFF5D2B4)
private val FaceLine = Stroke(1.45f, cap = StrokeCap.Round)

/** A small, decorative companion for a paper note. [modifier] can override its 72 dp canvas. */
@Composable
fun WaterSpirit(modifier: Modifier = Modifier, animated: Boolean = true) {
    val night = LocalDhmoExtraColors.current.isNight
    val time by rememberAmbientTime(rememberCozyMotionEnabled(animated))
    Canvas(modifier.size(72.dp)) {
        val s = minOf(size.width, size.height) / 76f
        translate(size.width / 2f, size.height / 2f + 25f * s) {
            scale(s, Offset.Zero) { drawWaterSpirit(time, sip = 1f, celebrating = false, night = night) }
        }
    }
}

internal fun DrawScope.drawWaterSpirit(time: Float, sip: Float, celebrating: Boolean, night: Boolean) {
    val pose = sipPose(sip, celebrating)
    val breath = (1f - cos(time * 1.50f)) / 2f
    val stretch = pose.stretch + 0.024f * breath
    val shadow = 1f - pose.height / 70f
    drawOval(Ink.copy(alpha = 0.19f * shadow), Offset(-20f * shadow, -2f), Size(40f * shadow, 5f))
    translate(-2.5f * pose.height / 20f, -pose.height) {
        rotate(pose.lean, Offset.Zero) {
            scale(1f / (1f + stretch), 1f + stretch, Offset.Zero) {
                drawPath(Body, if (night) Color(0xFFBCD9CE) else Color(0xFFD8EADF))
                drawPath(BodyShade, if (night) Color(0xFF92B6AD) else Color(0xFFAECCBD))
                drawPath(Body, Ink, style = Stroke(1.45f))
                drawPath(RimLight, Color(0xFFFFF8E0), style = Stroke(2.3f, cap = StrokeCap.Round))
                drawCircle(Color(0xFFEDF6E2), 1f, Offset(-15.8f, -17.8f))
                drawPath(Belly, Color(0xFFDFEEDB), style = Stroke(1f, cap = StrokeCap.Round))
                drawSpiritFace(time, pose.delight)
                rotate(pose.leafAngle + 3f * sin(time * 1.50f), Offset(-3f, -36f)) {
                    drawPath(Leaf, Moss)
                    drawPath(LeafLight, Color(0xFFA8BC83))
                    drawPath(Leaf, LeafInk, style = Stroke(1.2f))
                    drawPath(Veins, LeafInk, style = Stroke(0.95f, cap = StrokeCap.Round))
                }
            }
        }
    }
}

private fun DrawScope.drawSpiritFace(time: Float, delight: Float) {
    val blink = pulse(time % 6.3f, 4.76f, 4.85f, 4.99f)
    val openness = (1f - blink) * (1f - delight)
    val gaze = -0.8f - 0.6f * sin(time * 0.74f)
    for (side in -1..1 step 2) {
        val x = side * 7.7f + gaze
        val y = -17.3f
        val eyeHeight = 1.2f + 4.1f * openness
        drawOval(Ink, Offset(x - 1.95f, y - eyeHeight / 2f), Size(3.9f, eyeHeight), alpha = 1f - delight)
        drawCircle(Color(0xFFFFFCEB), 0.68f, Offset(x - 0.5f, y - 0.85f), alpha = openness)
        if (delight > 0f) {
            translate(x, y + 0.3f) { drawPath(HappyEye, Ink, alpha = delight, style = FaceLine) }
        }
        val cheek = Offset(side * 13.1f - 1f, -12.4f)
        drawOval(Blush, cheek - Offset(3.5f, 1.7f), Size(7f, 3.4f))
        drawLine(CheekLight, cheek - Offset(1.1f, 0f), cheek - Offset(0.4f, 1f), 0.9f, StrokeCap.Round)
    }
    translate(gaze / 2f, 0f) {
        drawPath(Smile, Ink, style = FaceLine)
        drawOval(Ink, Offset(-2.4f, -11.1f), Size(4.8f, 3.4f), alpha = delight)
    }
}

private val FlyGold = Color(0xFFFFD683)
private val FireflyGlow = Brush.radialGradient(listOf(FlyGold.copy(alpha = 0.5f), Color.Transparent), center = Offset.Zero, radius = 6f)

/** Staggered arrivals and continuously curving paths keep the fireflies distinct from a confetti burst. */
internal fun DrawScope.drawGoalFireflies(goal: Float) {
    if (goal <= 0f || goal >= 1f) return
    repeat(11) { index ->
        val seed = index * 2.39996f
        val start = 0.06f + (index % 4) * 0.065f
        val t = interval(goal, start, start + 0.68f)
        val opacity = pulse(t, 0f, 0.24f, 1f)
        if (opacity > 0f) {
            val x = 28f + (index * 43 % 173) + 10f * (sin(seed + t * 6f) - sin(seed)) + t * 9f * cos(seed)
            val y = 241f - (index * 13 % 52) - 72f * smoothStep(t) - 8f * sin(t * 3.14f + seed)
            translate(x, y) {
                drawCircle(FireflyGlow, 6f, Offset.Zero, alpha = opacity)
                drawCircle(Color(0xFFFFF1BC), 1.35f, Offset.Zero, alpha = opacity)
                if (index % 3 == 0) drawStar(Offset.Zero, 6.7f, Color(0xFFFFE5A6).copy(alpha = opacity))
            }
        }
    }
}
