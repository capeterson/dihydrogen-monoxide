@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.feature.settings.backupfile

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import casa.peterson.dhmo.core.designsystem.component.PlaceholderScreen
import casa.peterson.dhmo.feature.settings.R
import casa.peterson.dhmo.feature.settings.navigation.SettingsIntegrations

@Composable
internal fun BackupSettingsScreen(onBack: () -> Unit, integrations: SettingsIntegrations, onOpenRestore: () -> Unit) {
    PlaceholderScreen(title = stringResource(R.string.feature_settings_backup_settings_title))
}
