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
import casa.peterson.dhmo.core.common.ApplicationScope
import casa.peterson.dhmo.core.data.repository.DeviceLocationSource
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.repository.WeatherController
import casa.peterson.dhmo.core.designsystem.component.DhmoScreen
import casa.peterson.dhmo.core.model.settings.WeatherMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltViewModel
internal class WeatherRationaleViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val controller: WeatherController,
    private val location: DeviceLocationSource,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    val deviceSupported: Boolean = location.isSupported()

    fun hasPermission(): Boolean = location.hasPermission()

    fun useDeviceLocation(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.update { it.copy(weather = it.weather.copy(mode = WeatherMode.DEVICE_LOCATION)) }
            onDone()
            // A location fix and a forecast can take a long time (or fail); setup must not wait for them.
            appScope.launch { controller.onSettingsChanged() }
        }
    }
}
