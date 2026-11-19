package casa.peterson.dhmo.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import casa.peterson.dhmo.core.designsystem.R
import casa.peterson.dhmo.core.designsystem.icon.DhmoIcons
import casa.peterson.dhmo.core.designsystem.theme.NumeralLabelStyle
import casa.peterson.dhmo.core.domain.format.VolumeFormatter
import casa.peterson.dhmo.core.model.settings.VolumeUnit

/** The minimum touch target for every interactive element (Material and WCAG guidance). */
val MinTouchTarget = 48.dp

private const val COASTER_PRESS_WIDTH = 0.025f
private const val COASTER_PRESS_HEIGHT = 0.055f
private const val COASTER_PRESS_TILT = -1.2f

/** A coaster: a round-topped paper disc with a dashed edge, the design's `.coaster`. */
private val CoasterShape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp, bottomEnd = 28.dp, bottomStart = 28.dp)

/**
 * A one-tap "add this much" coaster, at least 48 dp by 48 dp. With a [label] (the container's name) and [icon] (its inked
 * drawing) the name and amount stack under the icon, as in the design; without them it is a "+ 250 ml" button. It dips
 * and tilts a little while pressed. TalkBack reads "Add 250 ml", or "Add Glass, 250 ml" with a label.
 */
@Composable
fun QuickAddButton(
    amountMl: Int,
    unit: VolumeUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    @DrawableRes icon: Int? = null,
) {
    val formatted = VolumeFormatter.format(amountMl, unit, currentLocale())
    val description =
        if (label == null) {
            stringResource(R.string.core_designsystem_quick_add_description, formatted)
        } else {
            stringResource(R.string.core_designsystem_quick_add_named_description, label, formatted)
        }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motion = rememberCozyMotionEnabled(animated = true)
    val dip by animateFloatAsState(
        targetValue = if (pressed && motion) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.69f, stiffness = 560f),
        label = "coasterPress",
    )
    val edge = MaterialTheme.colorScheme.outline
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .defaultMinSize(minWidth = MinTouchTarget, minHeight = MinTouchTarget)
                .graphicsLayer {
                    val press = if (motion) dip else 0f
                    scaleX = 1f - COASTER_PRESS_WIDTH * press
                    scaleY = 1f - COASTER_PRESS_HEIGHT * press
                    rotationZ = COASTER_PRESS_TILT * press
                }
                .drawBehind {
                    val stroke = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
                    drawPath(
                        Path().apply {
                            addOutline(CoasterShape.createOutline(size, layoutDirection, this@drawBehind))
                        },
                        edge,
                        style = stroke,
                    )
                },
        enabled = enabled,
        shape = CoasterShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        interactionSource = interaction,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp).clearAndSetSemantics { contentDescription = description },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            if (label == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(painterResource(DhmoIcons.Add), contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(text = formatted, style = NumeralLabelStyle, maxLines = 1)
                }
            } else {
                Icon(painterResource(icon ?: DhmoIcons.Glass), contentDescription = null, modifier = Modifier.size(22.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    formatted,
                    style = NumeralLabelStyle.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
