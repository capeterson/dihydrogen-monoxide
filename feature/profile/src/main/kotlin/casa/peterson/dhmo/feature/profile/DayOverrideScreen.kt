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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.core.designsystem.component.DhmoScreen
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.domain.format.VolumeFormatter
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun DayOverrideScreen(onBack: () -> Unit, viewModel: DayOverrideViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect { onBack() } }
    DhmoScreen(topBar = { DhmoTopBar(stringResource(R.string.feature_profile_day_override_title), onNavigateBack = onBack) }) { padding ->
        DayOverrideContent(
            state,
            onTargetChange = viewModel::onTargetChange,
            onReasonChange = viewModel::onReasonChange,
            onSave = viewModel::onSave,
            onClear = viewModel::onClear,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        )
    }
}

@Composable
internal fun DayOverrideContent(
    state: DayOverrideUiState,
    onTargetChange: (String) -> Unit,
    onReasonChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val day = state.day
        if (state.loading || day == null) {
            Text(stringResource(R.string.feature_profile_loading))
            return@Column
        }
        Text(
            stringResource(
                R.string.feature_profile_day_override_for,
                day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)),
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        state.withoutOverrideMl?.let {
            Text(stringResource(R.string.feature_profile_day_override_calculated, VolumeFormatter.format(it, state.units, locale)))
        }
        FormTextField(
            stringResource(R.string.feature_profile_day_override_amount, VolumeFormatter.symbol(state.units)),
            state.target.text,
            onTargetChange,
            isError = state.invalid,
        )
        FormTextField(
            stringResource(R.string.feature_profile_day_override_reason),
            state.reason,
            onReasonChange,
            keyboardType = KeyboardType.Text,
        )
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
        if (state.hasOverride) {
            OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.feature_profile_remove_override))
            }
        }
    }
}
