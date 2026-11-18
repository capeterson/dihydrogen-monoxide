package casa.peterson.dhmo.feature.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.data.repository.DeviceLocationSource
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.repository.WeatherController
import casa.peterson.dhmo.core.designsystem.component.DhmoScreen
import casa.peterson.dhmo.core.model.settings.WeatherMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Explains what the weather feature sends before it is switched on. Choosing a city is done later in
 * Settings → Weather; here the choices are the approximate device location or nothing.
 */
@Composable
internal fun WeatherRationaleScreen(onDone: () -> Unit, viewModel: WeatherRationaleViewModel = hiltViewModel()) {
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.useDeviceLocation(onDone) else onDone()
    }
    DhmoScreen { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.feature_onboarding_weather_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.feature_onboarding_weather_body), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.feature_onboarding_weather_privacy), style = MaterialTheme.typography.bodyMedium)
            if (viewModel.deviceSupported) {
                Button(
                    onClick = {
                        if (viewModel.hasPermission()) {
                            viewModel.useDeviceLocation(
                                onDone,
                            )
                        } else {
                            permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.feature_onboarding_weather_use_location)) }
            }
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.feature_onboarding_weather_skip))
            }
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.feature_onboarding_weather_city_later))
            }
        }
    }
}
