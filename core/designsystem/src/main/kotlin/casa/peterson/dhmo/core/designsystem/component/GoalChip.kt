package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import casa.peterson.dhmo.core.designsystem.R
import casa.peterson.dhmo.core.domain.format.VolumeFormatter
import casa.peterson.dhmo.core.model.settings.VolumeUnit

/** A luggage tag showing the day's goal. Pass [onClick] to make it a button (for example to open the goal editor). */
@Composable
fun GoalChip(goalMl: Int, unit: VolumeUnit, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val formatted = VolumeFormatter.format(goalMl, unit, currentLocale())
    val description = stringResource(R.string.core_designsystem_goal_chip_description, formatted)
    TagChip(
        label = stringResource(R.string.core_designsystem_goal_chip_label, formatted),
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        onClick = onClick,
    )
}
