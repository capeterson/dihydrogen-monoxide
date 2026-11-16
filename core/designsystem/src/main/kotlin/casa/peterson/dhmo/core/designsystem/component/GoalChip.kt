package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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

/** A pill showing the day's goal. Pass [onClick] to make it a button (for example to open the goal editor). */
@Composable
fun GoalChip(goalMl: Int, unit: VolumeUnit, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val formatted = VolumeFormatter.format(goalMl, unit, currentLocale())
    val label = stringResource(R.string.core_designsystem_goal_chip_label, formatted)
    val description = stringResource(R.string.core_designsystem_goal_chip_description, formatted)
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).clearAndSetSemantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(painterResource(DhmoIcons.WaterDrop), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
    val color = MaterialTheme.colorScheme.secondaryContainer
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    if (onClick == null) {
        Surface(modifier = modifier, shape = CircleShape, color = color, contentColor = contentColor, content = content)
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier.minimumInteractiveComponentSize(),
            shape = CircleShape,
            color = color,
            contentColor = contentColor,
            content = content,
        )
    }
}
