package casa.peterson.dhmo.feature.settings.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.core.designsystem.component.DhmoScreen
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.designsystem.component.SectionCard
import casa.peterson.dhmo.core.designsystem.component.SwitchRow
import casa.peterson.dhmo.core.model.settings.StatsSettings
import casa.peterson.dhmo.feature.settings.R

@Composable
internal fun StatsSettingsScreen(onBack: () -> Unit, viewModel: StatsSettingsViewModel = hiltViewModel()) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    StatsSettingsContent(stats, onBack, viewModel::setIncludeNoLogDays, viewModel::setWeeklyStreakFreeze)
}

@Composable
internal fun StatsSettingsContent(
    stats: StatsSettings,
    onBack: () -> Unit,
    onIncludeNoLogDays: (Boolean) -> Unit,
    onWeeklyFreeze: (Boolean) -> Unit,
) {
    val title = stringResource(R.string.feature_settings_stats_settings_title)
    DhmoScreen(topBar = { DhmoTopBar(title, onNavigateBack = onBack) }) { padding ->
        Column(
            Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                SwitchRow(
                    stringResource(R.string.feature_settings_include_no_log_days),
                    checked = stats.includeNoLogDays,
                    onCheckedChange = onIncludeNoLogDays,
                    summary = stringResource(R.string.feature_settings_include_no_log_days_summary),
                )
                SwitchRow(
                    stringResource(R.string.feature_settings_weekly_freeze),
                    checked = stats.weeklyStreakFreeze,
                    onCheckedChange = onWeeklyFreeze,
                    summary = stringResource(R.string.feature_settings_weekly_freeze_summary),
                )
            }
        }
    }
}
