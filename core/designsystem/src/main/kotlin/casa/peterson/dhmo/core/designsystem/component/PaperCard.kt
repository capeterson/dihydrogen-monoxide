package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import casa.peterson.dhmo.core.designsystem.theme.LocalDhmoExtraColors

private val DayShadow = Color(0xFF22302B)
private val NightRim = Color(0x14F2B661)

/**
 * A sheet of paper: a warm, diffuse shadow in the day theme and, at night, a deeper one with a faint lamp-lit rim along
 * the top edge. [containerColor] defaults to the card surface; the Night Sky card passes the star-field colour.
 */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable ColumnScope.() -> Unit,
) {
    val night = LocalDhmoExtraColors.current.isNight
    val tint = if (night) Color.Black else DayShadow
    Surface(
        modifier =
            modifier
                .shadow(if (night) 10.dp else 6.dp, shape, ambientColor = tint, spotColor = tint)
                .drawWithContent {
                    drawContent()
                    if (night) {
                        drawLine(
                            NightRim,
                            androidx.compose.ui.geometry.Offset(0f, 0.5f),
                            androidx.compose.ui.geometry.Offset(size.width, 0.5f),
                            1.dp.toPx(),
                        )
                    }
                },
        shape = shape,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(content = content)
    }
}
