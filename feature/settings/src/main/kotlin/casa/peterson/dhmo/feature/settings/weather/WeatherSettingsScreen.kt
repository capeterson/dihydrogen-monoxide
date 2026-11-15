@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.feature.settings.weather

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import casa.peterson.dhmo.core.designsystem.component.PlaceholderScreen
import casa.peterson.dhmo.feature.settings.R
import casa.peterson.dhmo.feature.settings.navigation.SettingsIntegrations

@Composable
internal fun WeatherSettingsScreen(onBack: () -> Unit, onOpenCitySearch: () -> Unit, integrations: SettingsIntegrations) {
    PlaceholderScreen(title = stringResource(R.string.feature_settings_weather_settings_title))
}
