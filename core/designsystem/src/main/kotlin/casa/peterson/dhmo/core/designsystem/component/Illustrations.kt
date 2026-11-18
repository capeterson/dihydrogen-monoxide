@file:Suppress("MagicNumber") // Coordinates are ported from the design's 40x56, 38x56, 110x110 and 140x80 SVG artboards.

package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import casa.peterson.dhmo.core.designsystem.theme.LocalDhmoExtraColors

private fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()

private val LanternBody by lazy { svgPath("M8 16C8 9 32 9 32 16v24c0 7-24 7-24 0z") }
private val LanternRibs by lazy { svgPath("M8 23q12 5 24 0M8 31q12 5 24 0") }
private val StarShape by lazy { svgPath("M10 0c.7 5.6 3.8 8.8 10 10-6.2 1.2-9.3 4.4-10 10-.7-5.6-3.8-8.8-10-10C6.2 8.8 9.3 5.6 10 0z") }
private val PlantLeafA by lazy { svgPath("M19 36C9 34 4 24 6 14c10 0 15 8 13 22z") }
private val PlantLeafB by lazy { svgPath("M19 36c-1-12 6-22 15-24 3 10-3 20-15 24z") }
private val PlantLeafC by lazy { svgPath("M19 36C16 26 18 14 24 6c5 8 2 20-5 30z") }
private val PlantPot by lazy { svgPath("M9 40h20l-3 16H12z") }
private val BigLeaf by lazy { svgPath("M52 104C12 94 0 52 30 22 46 6 78 10 94 28c14 20 6 62-42 76z") }
private val BigLeafVeins by lazy { svgPath("M52 104V30M52 78 20 58M52 64 14 40M52 78 84 58M52 64 92 42") }
private val CatBody by lazy {
    svgPath("M18 66C14 46 34 34 62 34c20 0 34 6 40 16 4-6 12-8 18-4 6 4 6 12 0 14-2 2-4 3-6 3 4 6-2 11-10 11H30C24 70 20 68 18 66z")
}
private val CatEars by lazy { svgPath("M104 40l-2-12 8 6zM116 38l4-11 4 11z") }
private val CatEye by lazy { svgPath("M110 50q3 2 6 0") }
private val CatTail by lazy { svgPath("M20 66c-8-2-12-10-6-16") }
private val CatZs by lazy { svgPath("M114 16h5l-5 6h5M122 9h4l-4 5h4") }

/**
 * The 40x56 paper lantern, drawn like the design's `#lantern` symbol: a [fill]ed ribbed body with [stroke] outline, ribs,
 * caps and cords. Lit lanterns pass the glow as [fill] and its dark amber as [stroke]; unlit ones a paper tone.
 */
internal fun DrawScope.drawLantern(topLeft: Offset, width: Float, fill: Color, stroke: Color) {
    val s = width / 40f
    translate(topLeft.x, topLeft.y) {
        scale(s, s, Offset.Zero) {
            drawLine(stroke.copy(alpha = 0.7f), Offset(20f, 0f), Offset(20f, 6f), strokeWidth = 1.2f, cap = StrokeCap.Round)
            drawRoundRect(stroke, Offset(13f, 5f), Size(14f, 4f), CornerRadius(2f))
            drawPath(LanternBody, fill)
            drawPath(LanternBody, stroke, style = Stroke(width = 1.5f))
            drawPath(LanternRibs, stroke.copy(alpha = 0.7f), style = Stroke(width = 1.2f, cap = StrokeCap.Round))
            drawRoundRect(stroke, Offset(13f, 45f), Size(14f, 4f), CornerRadius(2f))
            drawLine(stroke.copy(alpha = 0.7f), Offset(20f, 49f), Offset(20f, 55f), strokeWidth = 1.2f, cap = StrokeCap.Round)
        }
    }
}

/** A four-point sparkle star centred at [center], [diameter] across. */
internal fun DrawScope.drawStar(center: Offset, diameter: Float, color: Color) {
    val s = diameter / 20f
    translate(center.x - diameter / 2f, center.y - diameter / 2f) {
        scale(s, s, Offset.Zero) { drawPath(StarShape, color) }
    }
}

/** The windowsill plant: three leaves in a pot. [width] is the artboard width (38 units). */
internal fun DrawScope.drawPlant(topLeft: Offset, width: Float, leaf: Color, pot: Color, rim: Color) {
    val s = width / 38f
    translate(topLeft.x, topLeft.y) {
        scale(s, s, Offset.Zero) {
            drawPath(PlantLeafC, leaf.copy(alpha = 0.7f))
            drawPath(PlantLeafB, leaf.copy(alpha = 0.85f))
            drawPath(PlantLeafA, leaf)
            drawPath(PlantPot, pot)
            drawRoundRect(rim, Offset(8f, 38f), Size(22f, 4f), CornerRadius(2f))
        }
    }
}

/** A sleeping cat on a sill, the empty state's mascot. */
@Composable
fun SleepingCat(modifier: Modifier = Modifier, width: Dp = 132.dp) {
    val extras = LocalDhmoExtraColors.current
    val body = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    val dark = MaterialTheme.colorScheme.surface
    val zColor = MaterialTheme.colorScheme.secondary
    Canvas(modifier.width(width).aspectRatio(140f / 80f)) {
        scale(size.width / 140f, Offset.Zero) {
            drawRoundRect(extras.wood.copy(alpha = 0.8f), Offset(4f, 70f), Size(132f, 6f), CornerRadius(1.5f))
            drawPath(CatTail, body, style = Stroke(width = 5f, cap = StrokeCap.Round))
            drawPath(CatBody, body)
            drawPath(CatEars, body)
            drawPath(CatEye, dark, style = Stroke(width = 1.4f, cap = StrokeCap.Round))
            drawPath(CatZs, zColor, style = Stroke(width = 1.2f, cap = StrokeCap.Round))
        }
    }
}

/** A big leaf peeking over a card's corner: [Modifier] sets its size; it is drawn rotated like the design's recipe card. */
@Composable
fun CornerLeaf(modifier: Modifier = Modifier) {
    val leaf = MaterialTheme.colorScheme.tertiary
    val vein = MaterialTheme.colorScheme.surfaceContainerLow
    Canvas(modifier.aspectRatio(1f)) {
        scale(size.width / 110f, Offset.Zero) {
            rotate(18f, Offset(55f, 55f)) {
                drawPath(BigLeaf, leaf.copy(alpha = 0.95f))
                drawPath(BigLeafVeins, vein, style = Stroke(width = 3f, cap = StrokeCap.Round))
            }
        }
    }
}
