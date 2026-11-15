@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.feature.settings.stats

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import casa.peterson.dhmo.core.designsystem.component.PlaceholderScreen
import casa.peterson.dhmo.feature.settings.R

@Composable
internal fun StatsSettingsScreen(onBack: () -> Unit) {
    PlaceholderScreen(title = stringResource(R.string.feature_settings_stats_settings_title))
}
