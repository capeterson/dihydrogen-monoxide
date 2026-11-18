package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import casa.peterson.dhmo.core.designsystem.R
import casa.peterson.dhmo.core.designsystem.icon.DhmoIcons
import casa.peterson.dhmo.core.designsystem.theme.NumeralLabelStyle
import casa.peterson.dhmo.core.domain.format.VolumeFormatter
import casa.peterson.dhmo.core.model.settings.VolumeUnit

/** The minimum touch target for every interactive element (Material and WCAG guidance). */
val MinTouchTarget = 48.dp

private val CoasterPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)

/**
 * A one-tap "add this much" coaster, at least 48 dp by 48 dp. With a [label] (the container's name) the name sits
 * above the amount inside the button, so a row of them lines up and the name is never a separate, misaligned element.
 * TalkBack reads "Add 250 ml", or "Add Glass, 250 ml" with a label.
 */
@Composable
fun QuickAddButton(
    amountMl: Int,
    unit: VolumeUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
) {
    val formatted = VolumeFormatter.format(amountMl, unit, currentLocale())
    val description =
        if (label == null) {
            stringResource(R.string.core_designsystem_quick_add_description, formatted)
        } else {
            stringResource(R.string.core_designsystem_quick_add_named_description, label, formatted)
        }
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = MinTouchTarget, minHeight = MinTouchTarget),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        contentPadding = CoasterPadding,
    ) {
        Column(
            modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (label != null) {
                Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(painterResource(DhmoIcons.Add), contentDescription = null, modifier = Modifier.size(16.dp))
                Text(text = formatted, style = NumeralLabelStyle, maxLines = 1)
            }
        }
    }
}
