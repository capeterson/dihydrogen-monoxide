package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import casa.peterson.dhmo.core.designsystem.R
import casa.peterson.dhmo.core.designsystem.icon.DhmoIcons
import casa.peterson.dhmo.core.domain.format.VolumeFormatter
import casa.peterson.dhmo.core.model.settings.VolumeUnit

/** The minimum touch target for every interactive element (Material and WCAG guidance). */
val MinTouchTarget = 48.dp

/** A one-tap "add this much" button, at least 48 dp by 48 dp. TalkBack reads "Add 250 ml". */
@Composable
fun QuickAddButton(amountMl: Int, unit: VolumeUnit, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val formatted = VolumeFormatter.format(amountMl, unit, currentLocale())
    val description = stringResource(R.string.core_designsystem_quick_add_description, formatted)
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = MinTouchTarget, minHeight = MinTouchTarget),
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(painterResource(DhmoIcons.Add), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = formatted)
        }
    }
}
