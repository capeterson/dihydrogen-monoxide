package casa.peterson.dhmo.feature.settings.units

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** One option of a single-choice group: the whole row is the 56 dp target and is announced as a radio button. */
@Composable
internal fun ChoiceRow(title: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier, summary: String? = null) {
    ListItem(
        headlineContent = { Text(title) },
        modifier = modifier.defaultMinSize(minHeight = 56.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
