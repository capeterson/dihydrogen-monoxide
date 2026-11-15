@file:Suppress("UNUSED_PARAMETER", "LongParameterList")

package casa.peterson.dhmo.feature.settings.root

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import casa.peterson.dhmo.core.designsystem.component.PlaceholderScreen
import casa.peterson.dhmo.feature.settings.R

@Composable
internal fun SettingsScreen(
    onOpenProfile: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenUnits: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    onOpenWeather: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    PlaceholderScreen(title = stringResource(R.string.feature_settings_settings_title))
}
