package casa.peterson.dhmo.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.core.designsystem.component.DhmoScreen
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.designsystem.component.SwitchRow
import casa.peterson.dhmo.core.domain.format.VolumeFormatter
import java.time.DayOfWeek
import java.time.format.TextStyle

@Composable
internal fun GlobalOverrideScreen(onBack: () -> Unit, viewModel: GlobalOverrideViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.saved.collect { onBack() } }
    DhmoScreen(topBar = {
        DhmoTopBar(stringResource(R.string.feature_profile_global_override_title), onNavigateBack = onBack)
    }) { padding ->
        GlobalOverrideContent(
            state,
            onEnabledChange = viewModel::onEnabledChange,
            onTargetChange = viewModel::onTargetChange,
            onWeekdayChange = viewModel::onWeekdayChange,
            onSave = viewModel::onSave,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        )
    }
}

@Composable
internal fun GlobalOverrideContent(
    state: GlobalOverrideUiState,
    onEnabledChange: (Boolean) -> Unit,
    onTargetChange: (String) -> Unit,
    onWeekdayChange: (DayOfWeek, String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val symbol = VolumeFormatter.symbol(state.units)
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.loading) {
            Text(stringResource(R.string.feature_profile_loading))
            return@Column
        }
        SwitchRow(
            stringResource(R.string.feature_profile_global_enable),
            state.enabled,
            onEnabledChange,
            summary = stringResource(R.string.feature_profile_global_enable_summary),
        )
        FormTextField(
            stringResource(R.string.feature_profile_global_amount, symbol),
            state.target.text,
            onTargetChange,
            isError = state.invalid,
        )
        Text(
            stringResource(R.string.feature_profile_global_weekday_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.feature_profile_global_weekday_hint), style = MaterialTheme.typography.bodySmall)
        DayOfWeek.entries.forEach { day ->
            FormTextField(
                "${day.getDisplayName(TextStyle.FULL, locale)} ($symbol)",
                state.perWeekday.getValue(day).text,
                { onWeekdayChange(day, it) },
                isError = state.invalid,
            )
        }
        if (state.invalid) {
            ErrorText(
                stringResource(
                    R.string.feature_profile_global_invalid,
                    VolumeFormatter.format(MANUAL_TARGET_RANGE.first, state.units, locale),
                    VolumeFormatter.format(MANUAL_TARGET_RANGE.last, state.units, locale),
                ),
            )
        }
        Button(onClick = onSave, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.feature_profile_save))
        }
    }
}
