package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import casa.peterson.dhmo.core.designsystem.R
import kotlin.math.roundToInt

private const val PERCENT = 100f
private const val START_ANGLE = -90f
private const val FULL_CIRCLE = 360f

/** The fraction of the arc to draw: [progress] clamped to 0..1, so overshooting the goal never wraps the ring. */
internal fun ringFraction(progress: Float): Float = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)

/** The whole percent announced to TalkBack. Not capped, so 120 percent of the goal is reported as such. */
internal fun ringPercent(progress: Float): Int = if (progress.isNaN()) 0 else (progress.coerceAtLeast(0f) * PERCENT).roundToInt()

/**
 * A circular progress ring. [progress] is a fraction of the goal (1.0 is the goal); the arc stops at a full circle
 * for anything above 1.0. TalkBack announces "N percent of goal" (the visible [content] is not read separately).
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    strokeWidth: Dp = 18.dp,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    description: String =
        pluralStringResource(R.plurals.core_designsystem_progress_ring_description, ringPercent(progress), ringPercent(progress)),
    content: @Composable BoxScope.() -> Unit = {},
) {
    val fraction = ringFraction(progress)
    Box(
        modifier =
            modifier
                .size(size)
                .clearAndSetSemantics {
                    contentDescription = description
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2f
            val arcSize = Size(this.size.width - stroke.width, this.size.height - stroke.width)
            val topLeft = Offset(inset, inset)
            drawArc(trackColor, 0f, FULL_CIRCLE, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
            if (fraction > 0f) {
                drawArc(
                    progressColor,
                    START_ANGLE,
                    FULL_CIRCLE * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
            }
        }
        content()
    }
}
