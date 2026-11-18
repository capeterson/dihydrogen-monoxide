package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import casa.peterson.dhmo.core.designsystem.theme.NumeralFontFamily

/** A luggage tag: a flat chip with its left end cut to a point, like the design's `.tag` polygon. */
private object TagShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val point = with(density) { 12.dp.toPx() }
        return Outline.Generic(
            Path().apply {
                moveTo(point, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, size.height)
                lineTo(point, size.height)
                lineTo(0f, size.height / 2f)
                close()
            },
        )
    }
}

/** A tag with a [label] and an optional monospaced [value] ("Weight  72 kg"); pass [onClick] to make it a button. */
@Composable
fun TagChip(label: String, modifier: Modifier = Modifier, value: String? = null, onClick: (() -> Unit)? = null) {
    val hole = MaterialTheme.colorScheme.surface
    val base =
        if (onClick == null) {
            modifier
        } else {
            modifier.minimumInteractiveComponentSize().clip(TagShape).clickable(role = Role.Button, onClick = onClick)
        }
    Row(
        modifier =
            base
                .clip(TagShape)
                .drawBehind { drawCircle(hole, 3.dp.toPx(), Offset(13.dp.toPx(), size.height / 2f)) }
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(start = 26.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = NumeralFontFamily, fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
