package casa.peterson.dhmo.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private val RowMinHeight = 56.dp

/** A settings row: [title], optional [summary] and leading icon, an optional [trailing] slot, clickable when [onClick] is set. */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    ListItem(
        headlineContent = { Text(title) },
        modifier = modifier.defaultMinSize(minHeight = RowMinHeight).then(clickModifier),
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = leadingIcon?.let { { Icon(painterResource(it), contentDescription = null) } },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** A settings row with a switch. The whole row toggles and is announced as a switch with its on/off state. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        modifier =
            modifier
                .defaultMinSize(minHeight = RowMinHeight)
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = leadingIcon?.let { { Icon(painterResource(it), contentDescription = null) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
