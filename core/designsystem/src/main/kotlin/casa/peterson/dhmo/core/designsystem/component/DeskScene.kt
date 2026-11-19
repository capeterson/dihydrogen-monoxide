@file:Suppress("MagicNumber") // Coordinates are the design's 112x76 desk artboard.

package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import casa.peterson.dhmo.core.designsystem.theme.LocalDhmoExtraColors
import kotlin.math.PI

private val ShadePath by lazy { PathParser().parsePathString("M30 26 44 14l14 16-12 8z").toPath() }
private val ArmPath by lazy { PathParser().parsePathString("M22 70V50l16-26").toPath() }
private val MugBody by lazy { PathParser().parsePathString("M72 40h28v18a10 10 0 0 1-10 10h-8a10 10 0 0 1-10-10z").toPath() }
private val MugHandle by lazy { PathParser().parsePathString("M100 45h4a6 6 0 0 1 0 12h-4").toPath() }
private val Steam by lazy {
    listOf("M79 34c-4-4 4-6 0-11", "M86 34c-4-4 4-6 0-12", "M93 34c-4-4 4-6 0-10").map { PathParser().parsePathString(it).toPath() }
}
private val SteamPeriods = listOf(3f, 3.8f, 2.6f)

/**
 * The desk in the corner of Today: a lamp with a cone of light and a steaming mug. With [animated] the steam curls and
 * rises (never when system animations are off).
 */
@Composable
fun DeskScene(modifier: Modifier = Modifier, animated: Boolean = false) {
    val extras = LocalDhmoExtraColors.current
    val colors = MaterialTheme.colorScheme
    val time by rememberAmbientTime(animated && !rememberReducedMotion())
    Box(modifier.width(112.dp).height(76.dp)) {
        Canvas(Modifier.width(112.dp).height(76.dp)) {
            val s = size.width / 112f
            // The cone of lamplight under the shade.
            val cone = Path().apply {
                moveTo(-22f * s + 0.32f * 110f * s, 6f * s)
                lineTo(-22f * s + 0.66f * 110f * s, 6f * s)
                lineTo(-22f * s + 110f * s, 6f * s + 84f * s)
                lineTo(-22f * s, 6f * s + 84f * s)
                close()
            }
            clipPath(cone) {
                drawRect(
                    Brush.radialGradient(
                        listOf(extras.glow.copy(alpha = 0.25f), Color.Transparent),
                        Offset(-22f * s + 0.45f * 110f * s, 6f * s),
                        110f * s * 0.8f,
                    ),
                    Offset(-22f * s, 6f * s),
                    Size(110f * s, 84f * s),
                )
            }
            scale(s, s, Offset.Zero) {
                drawOval(colors.onSurface, Offset(6f, 68.5f), Size(32f, 7f))
                drawPath(ArmPath, colors.onSurface, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(ShadePath, colors.primary)
                drawCircle(extras.glow, 3.5f, Offset(50f, 32f))
                drawCircle(colors.onSurface, 3f, Offset(38f, 24f))
                drawPath(MugBody, colors.surfaceVariant)
                drawPath(MugBody, colors.onSurface, style = Stroke(1.8f))
                drawPath(MugHandle, colors.onSurfaceVariant, style = Stroke(2.4f, cap = StrokeCap.Round))
                drawRect(colors.primary, Offset(74f, 44f), Size(24f, 3f))
                val rise = if (time == 0f) 0f else -3f * (1f - kotlin.math.cos(time * 2f * PI.toFloat() / 3.2f)) / 2f
                val fade = if (time == 0f) 0.85f else 0.85f - 0.5f * (1f - kotlin.math.cos(time * 2f * PI.toFloat() / 3.2f)) / 2f
                translate(0f, rise) {
                    Steam.forEachIndexed { i, path ->
                        val offset = if (time == 0f) 0f else -((time / SteamPeriods[i]) % 1f) * 22f
                        drawPath(
                            path,
                            colors.onSurfaceVariant.copy(alpha = fade),
                            style = Stroke(2f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 6f), offset)),
                        )
                    }
                }
            }
        }
    }
}
