package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** The tooth heights of a sheet torn from a notebook, in dp, repeated along the edge. */
private val Teeth = floatArrayOf(2f, 5f, 1f, 4f, 2f, 6f, 3f, 1f, 5f, 2f, 4f, 1f)
private val ToothWidth = 14.dp

/** A paper sheet whose top edge is torn: a ragged line of small, uneven teeth instead of a rounded corner. */
internal object TornTopShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val step = with(density) { ToothWidth.toPx() }
        val unit = with(density) { 1.dp.toPx() }
        return Outline.Generic(
            Path().apply {
                moveTo(0f, Teeth[0] * unit)
                var index = 1
                var x = step
                while (x < size.width) {
                    lineTo(x, Teeth[index % Teeth.size] * unit)
                    index++
                    x += step
                }
                lineTo(size.width, Teeth[index % Teeth.size] * unit)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            },
        )
    }
}
