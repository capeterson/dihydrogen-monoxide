package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import casa.peterson.dhmo.core.designsystem.R
import casa.peterson.dhmo.core.domain.format.VolumeFormatter
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt

private const val DEFAULT_MIN_ML = 10
private const val DEFAULT_MAX_ML = 3000
private const val DEFAULT_STEP_ML = 10
private const val ML_FIELD_DIGITS = 0
private const val LITRE_FIELD_DIGITS = 2
private const val OUNCE_FIELD_DIGITS = 1

private fun fieldDigits(unit: VolumeUnit): Int = when (unit) {
    VolumeUnit.ML -> ML_FIELD_DIGITS
    VolumeUnit.L -> LITRE_FIELD_DIGITS
    VolumeUnit.US_FL_OZ, VolumeUnit.IMP_FL_OZ -> OUNCE_FIELD_DIGITS
}

/** The text-field representation of [ml] in [unit]: no grouping, trailing zeros trimmed, the locale's decimal separator. */
internal fun volumeFieldText(ml: Int, unit: VolumeUnit, locale: Locale): String {
    val plain =
        BigDecimal.valueOf(VolumeFormatter.fromMl(ml, unit))
            .setScale(fieldDigits(unit), RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    return plain.replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)
}

/** Parses what the user typed (either `.` or `,` as the decimal mark) into whole millilitres, or null when it is not a number. */
internal fun parseVolumeField(text: String, unit: VolumeUnit): Int? {
    val value = text.trim().replace(',', '.').toDoubleOrNull()
    val usable = value != null && value.isFinite() && value >= 0.0
    return if (usable) VolumeFormatter.toMl(checkNotNull(value), unit) else null
}

/**
 * A modal bottom sheet for entering an amount: a numeric field and a slider kept in sync, in the user's [unit].
 * [onConfirm] receives whole millilitres within [minMl]..[maxMl].
 */
@Composable
fun VolumeInputSheet(
    title: String,
    initialMl: Int,
    unit: VolumeUnit,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String = stringResource(R.string.core_designsystem_volume_sheet_confirm),
    minMl: Int = DEFAULT_MIN_ML,
    maxMl: Int = DEFAULT_MAX_ML,
    stepMl: Int = DEFAULT_STEP_ML,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        VolumeInputSheetContent(
            title = title,
            initialMl = initialMl,
            unit = unit,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            confirmLabel = confirmLabel,
            minMl = minMl,
            maxMl = maxMl,
            stepMl = stepMl,
        )
    }
}

/** The body of [VolumeInputSheet], stateless with respect to the sheet so it can be tested and previewed directly. */
@Composable
fun VolumeInputSheetContent(
    title: String,
    initialMl: Int,
    unit: VolumeUnit,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String = stringResource(R.string.core_designsystem_volume_sheet_confirm),
    minMl: Int = DEFAULT_MIN_ML,
    maxMl: Int = DEFAULT_MAX_ML,
    stepMl: Int = DEFAULT_STEP_ML,
) {
    val locale = currentLocale()
    val start = initialMl.coerceIn(minMl, maxMl)
    var text by rememberSaveable(unit) { mutableStateOf(volumeFieldText(start, unit, locale)) }
    var sliderMl by rememberSaveable(unit) { mutableIntStateOf(start) }
    val parsedMl = parseVolumeField(text, unit)
    val isValid = parsedMl != null && parsedMl in minMl..maxMl
    val minText = VolumeFormatter.format(minMl, unit, locale)
    val maxText = VolumeFormatter.format(maxMl, unit, locale)

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input.filter { it.isDigit() || it == '.' || it == ',' }
                val ml = parseVolumeField(text, unit)
                if (ml != null && ml in minMl..maxMl) sliderMl = ml
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.core_designsystem_volume_sheet_field_label, VolumeFormatter.symbol(unit))) },
            singleLine = true,
            isError = !isValid,
            supportingText = {
                if (!isValid) Text(stringResource(R.string.core_designsystem_volume_sheet_error, minText, maxText))
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        val sliderDescription = stringResource(R.string.core_designsystem_volume_sheet_slider_description)
        val sliderState = VolumeFormatter.format(sliderMl, unit, locale)
        Slider(
            value = sliderMl.toFloat(),
            onValueChange = { raw ->
                val snapped = (raw / stepMl).roundToInt() * stepMl
                sliderMl = snapped.coerceIn(minMl, maxMl)
                text = volumeFieldText(sliderMl, unit, locale)
            },
            valueRange = minMl.toFloat()..maxMl.toFloat(),
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = sliderDescription
                stateDescription = sliderState
            },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = minText, style = MaterialTheme.typography.labelMedium)
            Text(text = maxText, style = MaterialTheme.typography.labelMedium)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.core_designsystem_cancel)) }
            Button(onClick = { parsedMl?.let(onConfirm) }, enabled = isValid) { Text(confirmLabel) }
        }
    }
}
