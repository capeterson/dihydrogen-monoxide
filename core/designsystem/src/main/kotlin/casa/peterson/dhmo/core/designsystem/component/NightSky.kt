@file:Suppress("MagicNumber", "MatchingDeclarationName") // Sizes and opacities are the design's `.cell` rules for the Night Sky card.

package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import casa.peterson.dhmo.core.designsystem.theme.DhmoExtraColors
import casa.peterson.dhmo.core.designsystem.theme.LocalDhmoExtraColors
import casa.peterson.dhmo.core.designsystem.theme.NumeralFontFamily
import kotlin.math.min

/**
 * One day in the Night Sky. [fullness] is the day's total over its goal (1.0 or more means the goal was met); null is a
 * day still in progress, drawn as a dashed outline. [NoData] is a day with nothing logged; a `null` entry is padding.
 */
class StarDay(val fullness: Float?, val noData: Boolean = false) {
    companion object {
        /** A day in range with nothing logged: a faint dot. (A `null` entry is only grid padding and draws nothing.) */
        val NoData = StarDay(null, noData = true)
    }
}

private const val MET = 1f
private const val MAX_FULLNESS = 1.05f

/**
 * Draws one star the way the design's `.cell` does: bigger and brighter for fuller days, amber with a halo when the goal
 * was met, a dashed outline while the day is still going and a faint dot when there is no data. [cell] is the square the
 * star sits in, in pixels, and [dp] converts design pixels.
 */
internal fun DrawScope.drawSkyCell(
    extras: DhmoExtraColors,
    center: Offset,
    cell: Float,
    day: StarDay?,
    dp: Float,
    scaleToCell: Boolean = false,
) {
    val fullness = day?.fullness
    when {
        day == null -> Unit

        day.noData -> drawCircle(extras.star.copy(alpha = 0.25f), cell * 0.08f, center)

        fullness == null -> {
            val d = if (scaleToCell) cell * 0.5f else 10f * dp
            outlineStar(extras.star, center, d, 1.2f * dp, PathEffect.dashPathEffect(floatArrayOf(2f * dp, 2f * dp)))
        }

        else -> {
            val met = fullness >= MET
            val size = if (scaleToCell) {
                cell * (0.4f + 0.6f * min(fullness, MAX_FULLNESS))
            } else {
                (6f + 10f * min(fullness, MAX_FULLNESS)) *
                    dp
            }
            val alpha = if (met) 1f else 0.3f + 0.7f * min(fullness, 1f)
            if (met) {
                val radius = cell / 2f + 4f * dp
                drawCircle(
                    Brush.radialGradient(0f to extras.glowHalo, 0.65f to Color.Transparent, center = center, radius = radius),
                    radius,
                    center,
                )
            }
            drawStar(center, size, (if (met) extras.glow else extras.star).copy(alpha = alpha))
        }
    }
}

private fun DrawScope.outlineStar(color: Color, center: Offset, diameter: Float, strokeWidth: Float, effect: PathEffect) {
    val scale = diameter / 20f
    val path = androidx.compose.ui.graphics.Path()
    // Re-trace the four-point star with straight-ish edges so a dash pattern reads at 10 dp.
    val c = diameter / 2f
    val inner = c * 0.28f
    path.moveTo(center.x, center.y - c)
    path.lineTo(center.x + inner, center.y - inner)
    path.lineTo(center.x + c, center.y)
    path.lineTo(center.x + inner, center.y + inner)
    path.lineTo(center.x, center.y + c)
    path.lineTo(center.x - inner, center.y + inner)
    path.lineTo(center.x - c, center.y)
    path.lineTo(center.x - inner, center.y - inner)
    path.close()
    drawPath(path, color, style = Stroke(width = strokeWidth.coerceAtLeast(scale), pathEffect = effect))
}

/** The starfield card shell: a dark sky with a [title] and [caption] in star-coloured text, holding [content]. */
@Composable
fun NightSkyPanel(title: String, caption: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val extras = LocalDhmoExtraColors.current
    val ink = extras.star
    PaperCard(
        modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
        containerColor = extras.starfield,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = ink)
            Text(caption, style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.75f))
            content()
        }
    }
}

/**
 * The Night Sky card: each day of the range is a star, [weekdayLabels] run along the top, and [days] fill the grid
 * a week to a row (a null entry is a day without data). It reads as one image to TalkBack, described by [description].
 * [starReveal] optionally resolves the decorative stars in sequence; semantics always describe the current data.
 */
@Composable
fun NightSkyCard(
    title: String,
    caption: String,
    days: List<StarDay?>,
    weekdayLabels: List<String>,
    description: String,
    modifier: Modifier = Modifier,
    starReveal: State<Float>? = null,
) {
    val extras = LocalDhmoExtraColors.current
    NightSkyPanel(title, caption, modifier) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            weekdayLabels.forEach {
                Text(
                    it,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = NumeralFontFamily),
                    color = extras.star.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        val columns = weekdayLabels.size
        val rows = (days.size + columns - 1) / columns
        Canvas(Modifier.fillMaxWidth().height(CellHeight * rows).clearAndSetSemantics { contentDescription = description }) {
            val cell = size.width / columns
            val phase = starReveal?.value ?: 1f
            days.forEachIndexed { index, day ->
                val center = Offset((index % columns + 0.5f) * cell, (index / columns + 0.5f) * CellHeight.toPx())
                val start = (index % columns) * 0.05f
                val reveal = smoothStep(interval(phase, start, start + 0.7f))
                scale(0.6f + 0.4f * reveal, center) {
                    drawSkyCell(extras, center, minOf(cell, CellHeight.toPx()), day, density)
                }
            }
        }
    }
}

private val CellHeight = 40.dp
