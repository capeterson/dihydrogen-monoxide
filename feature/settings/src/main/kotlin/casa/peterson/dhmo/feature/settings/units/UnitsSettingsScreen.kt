package casa.peterson.dhmo.feature.settings.units

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.core.designsystem.component.DhmoScreen
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.designsystem.component.SectionCard
import casa.peterson.dhmo.core.designsystem.component.TimePickerRow
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.feature.settings.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun UnitsSettingsScreen(onBack: () -> Unit, viewModel: UnitsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UnitsContent(
        state = state,
        onBack = onBack,
        onUnits = viewModel::setUnits,
        onDayStart = viewModel::requestDayStart,
        onConfirmDayStart = viewModel::confirmDayStart,
        onDismissDayStart = viewModel::dismissDayStart,
    )
}

@Suppress("LongParameterList") // Stateless content: one callback per user action.
@Composable
internal fun UnitsContent(
    state: UnitsUiState,
    onBack: () -> Unit,
    onUnits: (VolumeUnit) -> Unit,
    onDayStart: (Int) -> Unit,
    onConfirmDayStart: () -> Unit,
    onDismissDayStart: () -> Unit,
) {
    val title = stringResource(R.string.feature_settings_units_settings_title)
    DhmoScreen(topBar = { DhmoTopBar(title, onNavigateBack = onBack) }) { padding ->
        Column(
            Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = stringResource(R.string.feature_settings_units_heading)) {
                Column(Modifier.selectableGroup()) {
                    VolumeUnit.entries.forEach { unit ->
                        ChoiceRow(stringResource(unitLabel(unit)), selected = unit == state.units, onSelect = { onUnits(unit) })
                    }
                }
            }
            SectionCard(title = stringResource(R.string.feature_settings_day_start_heading)) {
                Text(stringResource(R.string.feature_settings_day_start_explainer))
                TimePickerRow(
                    stringResource(R.string.feature_settings_day_start),
                    state.dayStartMinutes,
                    onDayStart,
                )
                if (state.changingDayStart) Text(stringResource(R.string.feature_settings_day_start_applying))
            }
        }
    }
    state.pendingDayStart?.let { pending ->
        AlertDialog(
            onDismissRequest = onDismissDayStart,
            title = { Text(stringResource(R.string.feature_settings_day_start_confirm_title, dayStartLabel(pending))) },
            text = {
                Text(
                    stringResource(
                        R.string.feature_settings_day_start_confirm_body,
                        dayStartLabel(state.dayStartMinutes),
                        dayStartLabel(pending),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmDayStart) { Text(stringResource(R.string.feature_settings_day_start_confirm)) }
            },
            dismissButton = { TextButton(onClick = onDismissDayStart) { Text(stringResource(R.string.feature_settings_cancel)) } },
        )
    }
}

@StringRes
internal fun unitLabel(unit: VolumeUnit): Int = when (unit) {
    VolumeUnit.ML -> R.string.feature_settings_unit_ml
    VolumeUnit.L -> R.string.feature_settings_unit_l
    VolumeUnit.US_FL_OZ -> R.string.feature_settings_unit_us_fl_oz
    VolumeUnit.IMP_FL_OZ -> R.string.feature_settings_unit_imp_fl_oz
}

/** [minutes] after midnight as a short local time, e.g. `4:00 AM` or `04:00`. */
@Composable
internal fun dayStartLabel(minutes: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    return LocalTime.of(minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
}

private const val MINUTES_PER_HOUR = 60
