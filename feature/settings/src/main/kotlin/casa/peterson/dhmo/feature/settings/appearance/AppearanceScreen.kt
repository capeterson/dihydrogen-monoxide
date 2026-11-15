@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.feature.settings.appearance

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import casa.peterson.dhmo.core.designsystem.component.PlaceholderScreen
import casa.peterson.dhmo.feature.settings.R

@Composable
internal fun AppearanceScreen(onBack: () -> Unit) {
    PlaceholderScreen(title = stringResource(R.string.feature_settings_appearance_title))
}
