package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import casa.peterson.dhmo.core.designsystem.R
import casa.peterson.dhmo.core.domain.body.BodyInputs
import casa.peterson.dhmo.core.model.settings.BodyUnits

/**
 * The metric / imperial switch with the weight and height fields under it. Switching converts what is already typed
 * ([BodyInputs.switchTo]), so the person never re-enters anything.
 */
@Composable
fun BodyMeasurementFields(inputs: BodyInputs, onChange: (BodyInputs) -> Unit, modifier: Modifier = Modifier, isError: Boolean = false) {
    val imperial = inputs.units == BodyUnits.IMPERIAL
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.core_designsystem_units),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(
                BodyUnits.METRIC to R.string.core_designsystem_units_metric,
                BodyUnits.IMPERIAL to R.string.core_designsystem_units_imperial,
            ).forEachIndexed { index, (units, label) ->
                SegmentedButton(
                    selected = inputs.units == units,
                    onClick = { onChange(inputs.switchTo(units)) },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                ) { Text(stringResource(label)) }
            }
        }
        DecimalField(
            stringResource(if (imperial) R.string.core_designsystem_weight_lb else R.string.core_designsystem_weight_kg),
            inputs.weight,
            isError,
            Modifier.fillMaxWidth(),
        ) { onChange(inputs.copy(weight = it)) }
        if (imperial) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DecimalField(stringResource(R.string.core_designsystem_height_feet), inputs.heightFeet, isError, Modifier.weight(1f)) {
                    onChange(inputs.copy(heightFeet = it))
                }
                DecimalField(stringResource(R.string.core_designsystem_height_inches), inputs.heightInches, isError, Modifier.weight(1f)) {
                    onChange(inputs.copy(heightInches = it))
                }
            }
        } else {
            DecimalField(stringResource(R.string.core_designsystem_height_cm), inputs.heightCm, isError, Modifier.fillMaxWidth()) {
                onChange(inputs.copy(heightCm = it))
            }
        }
        Text(stringResource(R.string.core_designsystem_height_optional), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DecimalField(label: String, value: String, isError: Boolean, modifier: Modifier, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = isError,
        singleLine = true,
        modifier = modifier,
    )
}
