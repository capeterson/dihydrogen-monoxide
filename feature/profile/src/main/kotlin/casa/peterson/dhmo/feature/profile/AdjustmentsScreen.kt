package casa.peterson.dhmo.feature.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import casa.peterson.dhmo.core.model.settings.AdjustmentToggles
import casa.peterson.dhmo.core.model.settings.GoalSettings
import casa.peterson.dhmo.core.model.settings.PinnedInputs

@Composable
internal fun AdjustmentsScreen(onBack: () -> Unit, viewModel: AdjustmentsViewModel = hiltViewModel()) {
    val settings by viewModel.goalSettings.collectAsStateWithLifecycle()
    DhmoScreen(topBar = { DhmoTopBar(stringResource(R.string.feature_profile_adjustments_title), onNavigateBack = onBack) }) { padding ->
        val modifier = Modifier.padding(padding).consumeWindowInsets(padding)
        when (val current = settings) {
            null -> Text(stringResource(R.string.feature_profile_loading), modifier.padding(16.dp))
            else -> AdjustmentsContent(current, viewModel::setToggles, viewModel::setPinned, modifier)
        }
    }
}

@Composable
internal fun AdjustmentsContent(
    settings: GoalSettings,
    onToggles: (AdjustmentToggles) -> Unit,
    onPinned: (PinnedInputs) -> Unit,
    modifier: Modifier = Modifier,
) {
    val toggles = settings.adjustments
    val pinned = settings.pinned
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.feature_profile_adjustments_intro))
        SectionCard(title = stringResource(R.string.feature_profile_adjustments_title)) {
            ToggleRow(R.string.feature_profile_toggle_activity, toggles.activity) { onToggles(toggles.copy(activity = it)) }
            ToggleRow(R.string.feature_profile_toggle_climate, toggles.climate) { onToggles(toggles.copy(climate = it)) }
            ToggleRow(R.string.feature_profile_toggle_pregnancy, toggles.pregnancyLactation) {
                onToggles(toggles.copy(pregnancyLactation = it))
            }
            ToggleRow(R.string.feature_profile_toggle_altitude, toggles.altitude) { onToggles(toggles.copy(altitude = it)) }
        }
        SectionCard(title = stringResource(R.string.feature_profile_pinned_title)) {
            SwitchRow(
                stringResource(R.string.feature_profile_pin_weight),
                pinned.pinWeight,
                { onPinned(pinned.copy(pinWeight = it)) },
                summary = stringResource(R.string.feature_profile_pin_weight_summary),
            )
            SwitchRow(
                stringResource(R.string.feature_profile_pin_activity),
                pinned.pinActivityLevel,
                { onPinned(pinned.copy(pinActivityLevel = it)) },
                summary = stringResource(R.string.feature_profile_pin_activity_summary),
            )
        }
    }
}

@Composable
private fun ToggleRow(@StringRes title: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    SwitchRow(stringResource(title), checked, onChange)
}
