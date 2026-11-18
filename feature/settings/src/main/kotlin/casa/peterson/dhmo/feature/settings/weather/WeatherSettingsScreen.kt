package casa.peterson.dhmo.feature.settings.weather

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.core.designsystem.component.DhmoScreen
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.designsystem.component.SectionCard
import casa.peterson.dhmo.core.model.settings.WeatherMode
import casa.peterson.dhmo.feature.settings.R
import casa.peterson.dhmo.feature.settings.units.ChoiceRow

@Composable
internal fun WeatherSettingsScreen(
    onBack: () -> Unit,
    onOpenCitySearch: () -> Unit,
    viewModel: WeatherSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), viewModel::onLocationPermissionResult)
    val context = LocalContext.current
    WeatherSettingsContent(
        state = state,
        onBack = onBack,
        onOff = viewModel::setOff,
        onDevice = {
            if (viewModel.hasLocationPermission()) {
                viewModel.onLocationPermissionResult(true)
            } else {
                permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        },
        onCity = { viewModel.useCity(onOpenCitySearch) },
        onChangeCity = onOpenCitySearch,
        onRefresh = viewModel::refresh,
        onOpenAttribution = { context.startActivity(Intent(Intent.ACTION_VIEW, OPEN_METEO_URL.toUri())) },
    )
}

@Composable
internal fun WeatherSettingsContent(
    state: WeatherUiState,
    onBack: () -> Unit,
    onOff: () -> Unit,
    onDevice: () -> Unit,
    onCity: () -> Unit,
    onChangeCity: () -> Unit,
    onRefresh: () -> Unit,
    onOpenAttribution: () -> Unit,
) {
    val title = stringResource(R.string.feature_settings_weather_settings_title)
    DhmoScreen(topBar = { DhmoTopBar(title, onNavigateBack = onBack) }) { padding ->
        Column(
            Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.feature_settings_weather_intro), style = MaterialTheme.typography.bodyMedium)
            SectionCard {
                ChoiceRow(
                    stringResource(R.string.feature_settings_weather_mode_off),
                    selected = state.mode == WeatherMode.OFF,
                    onSelect = onOff,
                    summary = stringResource(R.string.feature_settings_weather_mode_off_summary),
                )
                ChoiceRow(
                    stringResource(R.string.feature_settings_weather_mode_device),
                    selected = state.mode == WeatherMode.DEVICE_LOCATION,
                    onSelect = { if (state.deviceSupported) onDevice() },
                    summary = stringResource(
                        if (state.deviceSupported) {
                            R.string.feature_settings_weather_mode_device_summary
                        } else {
                            R.string.feature_settings_weather_mode_device_unsupported
                        },
                    ),
                )
                ChoiceRow(
                    stringResource(R.string.feature_settings_weather_mode_city),
                    selected = state.mode == WeatherMode.MANUAL_CITY,
                    onSelect = onCity,
                    summary = stringResource(R.string.feature_settings_weather_mode_city_summary),
                )
            }
            if (state.permissionDenied) {
                Text(
                    stringResource(R.string.feature_settings_weather_permission_denied),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            state.city?.let { city ->
                TextButton(onClick = onChangeCity) {
                    Text(stringResource(R.string.feature_settings_weather_change_city, city.name))
                }
            }
            if (state.mode != WeatherMode.OFF) {
                Text(
                    stringResource(
                        if (state.lastFetchFailed) {
                            R.string.feature_settings_weather_status_failed
                        } else {
                            R.string.feature_settings_weather_status_ok
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onRefresh, enabled = !state.refreshing) {
                    Text(stringResource(R.string.feature_settings_weather_refresh))
                }
            }
            TextButton(onClick = onOpenAttribution) { Text(stringResource(R.string.feature_settings_weather_attribution)) }
        }
    }
}

private const val OPEN_METEO_URL = "https://open-meteo.com/"
