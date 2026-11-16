package casa.peterson.dhmo.feature.settings.root

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.core.designsystem.component.DhmoScreen
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.designsystem.component.SettingsRow
import casa.peterson.dhmo.core.designsystem.icon.DhmoIcons
import casa.peterson.dhmo.core.model.settings.ThemeMode
import casa.peterson.dhmo.core.model.settings.WeatherMode
import casa.peterson.dhmo.core.model.sync.DriveBackupState
import casa.peterson.dhmo.core.model.sync.HcAvailability
import casa.peterson.dhmo.feature.settings.R
import casa.peterson.dhmo.feature.settings.units.dayStartLabel
import casa.peterson.dhmo.feature.settings.units.unitLabel

@Suppress("LongParameterList") // The T0 navigation signature passes each destination separately.
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
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        actions = SettingsActions(
            onOpenProfile = onOpenProfile,
            onOpenReminders = onOpenReminders,
            onOpenUnits = onOpenUnits,
            onOpenAppearance = onOpenAppearance,
            onOpenStats = onOpenStats,
            onOpenExport = onOpenExport,
            onOpenBackup = onOpenBackup,
            onOpenHealthConnect = onOpenHealthConnect,
            onOpenWeather = onOpenWeather,
            onOpenAbout = onOpenAbout,
        ),
    )
}

@Composable
internal fun SettingsContent(state: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    val settings = state.settings
    DhmoScreen(modifier = modifier, topBar = { DhmoTopBar(stringResource(R.string.feature_settings_settings_title)) }) { padding ->
        LazyColumn(contentPadding = padding) {
            section(R.string.feature_settings_section_goal)
            item {
                SettingsRow(
                    stringResource(R.string.feature_settings_row_profile),
                    summary = stringResource(R.string.feature_settings_row_profile_summary),
                    leadingIcon = DhmoIcons.WaterDrop,
                    onClick = actions.onOpenProfile,
                )
            }
            item {
                val summary = when (settings?.reminders?.enabled) {
                    true -> R.string.feature_settings_on
                    false -> R.string.feature_settings_off
                    null -> null
                }
                SettingsRow(
                    stringResource(R.string.feature_settings_row_reminders),
                    summary = summary?.let { stringResource(it) },
                    leadingIcon = DhmoIcons.Notifications,
                    onClick = actions.onOpenReminders,
                )
            }
            section(R.string.feature_settings_section_general)
            item {
                SettingsRow(
                    stringResource(R.string.feature_settings_units_settings_title),
                    summary = settings?.let {
                        stringResource(
                            R.string.feature_settings_units_summary,
                            stringResource(unitLabel(it.units)),
                            dayStartLabel(it.dayStartMinutes),
                        )
                    },
                    onClick = actions.onOpenUnits,
                )
            }
            item {
                SettingsRow(
                    stringResource(R.string.feature_settings_appearance_title),
                    summary = settings?.let { stringResource(themeLabel(it.theme.mode)) },
                    onClick = actions.onOpenAppearance,
                )
            }
            item {
                SettingsRow(
                    stringResource(R.string.feature_settings_stats_settings_title),
                    summary = stringResource(R.string.feature_settings_stats_summary),
                    leadingIcon = DhmoIcons.BarChart,
                    onClick = actions.onOpenStats,
                )
            }
            section(R.string.feature_settings_section_data)
            item {
                SettingsRow(
                    stringResource(R.string.feature_settings_export_title),
                    summary = stringResource(R.string.feature_settings_export_summary),
                    onClick = actions.onOpenExport,
                )
            }
            item {
                SettingsRow(
                    stringResource(R.string.feature_settings_backup_settings_title),
                    summary = stringResource(driveLabel(state.driveState)),
                    leadingIcon = DhmoIcons.History,
                    onClick = actions.onOpenBackup,
                )
            }
            section(R.string.feature_settings_section_integrations)
            item {
                SettingsRow(
                    stringResource(R.string.feature_settings_row_health_connect),
                    summary = stringResource(healthConnectLabel(state.hcAvailability, settings?.healthConnect?.writeHydration == true)),
                    onClick = actions.onOpenHealthConnect,
                )
            }
            item {
                SettingsRow(
                    stringResource(R.string.feature_settings_weather_settings_title),
                    summary = settings?.let { stringResource(weatherLabel(it.weather.mode)) },
                    onClick = actions.onOpenWeather,
                )
            }
            section(R.string.feature_settings_section_about)
            item {
                SettingsRow(
                    stringResource(R.string.feature_settings_about_title),
                    summary = state.versionName.takeIf { it.isNotEmpty() }?.let {
                        stringResource(R.string.feature_settings_version, it)
                    },
                    onClick = actions.onOpenAbout,
                )
            }
        }
    }
}

private fun LazyListScope.section(@StringRes title: Int) {
    item(contentType = "header") {
        HorizontalDivider()
        Text(
            stringResource(title),
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp).semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@StringRes
internal fun themeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.feature_settings_theme_system
    ThemeMode.LIGHT -> R.string.feature_settings_theme_light
    ThemeMode.DARK -> R.string.feature_settings_theme_dark
}

@StringRes
internal fun weatherLabel(mode: WeatherMode): Int = when (mode) {
    WeatherMode.OFF -> R.string.feature_settings_off
    WeatherMode.DEVICE_LOCATION -> R.string.feature_settings_weather_device
    WeatherMode.MANUAL_CITY -> R.string.feature_settings_weather_city
}

@StringRes
internal fun healthConnectLabel(availability: HcAvailability, connected: Boolean): Int = when (availability) {
    HcAvailability.AVAILABLE -> if (connected) R.string.feature_settings_on else R.string.feature_settings_hc_available
    HcAvailability.PROVIDER_UPDATE_REQUIRED -> R.string.feature_settings_hc_update_required
    HcAvailability.UNAVAILABLE -> R.string.feature_settings_hc_unavailable
}

@StringRes
internal fun driveLabel(state: DriveBackupState): Int = when (state) {
    DriveBackupState.NotConfigured -> R.string.feature_settings_backup_not_configured
    DriveBackupState.SignedOut -> R.string.feature_settings_backup_signed_out
    is DriveBackupState.Ready -> R.string.feature_settings_backup_ready
    DriveBackupState.Running -> R.string.feature_settings_backup_running
    DriveBackupState.NeedsReconnect -> R.string.feature_settings_backup_reconnect
    is DriveBackupState.Error -> R.string.feature_settings_backup_error
}
