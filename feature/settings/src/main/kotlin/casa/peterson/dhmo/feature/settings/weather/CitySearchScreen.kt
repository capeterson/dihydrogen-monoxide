@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.feature.settings.weather

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import casa.peterson.dhmo.core.designsystem.component.PlaceholderScreen
import casa.peterson.dhmo.feature.settings.R

@Composable
internal fun CitySearchScreen(onBack: () -> Unit) {
    PlaceholderScreen(title = stringResource(R.string.feature_settings_city_search_title))
}
