@file:Suppress("MagicNumber") // The design's `.lan-mini` sizes: 26 by 36, 6 apart, halo 10 out.

package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import casa.peterson.dhmo.core.designsystem.theme.LocalDhmoExtraColors

private val LanternWidth = 26.dp
private val LanternHeight = 36.dp
private val LanternGap = 6.dp

/**
 * A row of [total] small paper lanterns with the last [lit] of them lit, one for each day of a streak. Unlit lanterns are
 * outlines. [description] is what TalkBack reads, for example "Six lanterns lit in a row".
 */
@Composable
fun LanternRow(lit: Int, total: Int, description: String, modifier: Modifier = Modifier) {
    val extras = LocalDhmoExtraColors.current
    val unlitStroke = MaterialTheme.colorScheme.onSurfaceVariant
    val litStroke = MaterialTheme.colorScheme.secondary
    val count = total.coerceAtLeast(1)
    Canvas(
        modifier
            .width(LanternWidth * count + LanternGap * (count - 1))
            .height(LanternHeight)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val w = LanternWidth.toPx()
        val gap = LanternGap.toPx()
        val firstLit = total - lit.coerceIn(0, total)
        repeat(total) { i ->
            val left = i * (w + gap)
            val isLit = i >= firstLit
            if (isLit) {
                val center = Offset(left + w / 2f, size.height / 2f)
                val radius = size.height / 2f + 10.dp.toPx()
                drawCircle(
                    Brush.radialGradient(0f to extras.glowHalo, 0.65f to Color.Transparent, center = center, radius = radius),
                    radius,
                    center,
                )
            }
            drawLantern(Offset(left, 0f), w, if (isLit) extras.glow else Color.Transparent, if (isLit) litStroke else unlitStroke)
        }
    }
}
