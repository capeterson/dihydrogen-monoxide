package casa.peterson.dhmo.feature.insights

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import casa.peterson.dhmo.core.designsystem.component.SettingsRow
import casa.peterson.dhmo.core.designsystem.component.SwitchRow
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Why a custom range is not allowed, or null when it is. */
@StringRes
internal fun rangeError(from: LocalDate, to: LocalDate, today: LocalDate): Int? = when {
    to.isBefore(from) -> R.string.feature_insights_picker_error_order

    to.isAfter(today) -> R.string.feature_insights_picker_error_future

    java.time.temporal.ChronoUnit.DAYS.between(
        from,
        to,
    ) + 1 > RangeSelection.MAX_CUSTOM_DAYS -> R.string.feature_insights_picker_error_length

    else -> null
}

/** The custom range dialog: presets, explicit start and end dates, and the optional comparison with the previous period. */
@Composable
internal fun RangePickerDialog(
    initialFrom: LocalDate,
    initialTo: LocalDate,
    initialCompare: Boolean,
    today: LocalDate,
    onApply: (LocalDate, LocalDate, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var from by rememberSaveable { mutableLongStateOf(initialFrom.toEpochDay()) }
    var to by rememberSaveable { mutableLongStateOf(initialTo.toEpochDay()) }
    var compare by rememberSaveable { mutableStateOf(initialCompare) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val fromDate = LocalDate.ofEpochDay(from)
    val toDate = LocalDate.ofEpochDay(to)
    val error = rangeError(fromDate, toDate, today)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_insights_custom_range_title)) },
        text = {
            RangePickerContent(
                from = fromDate,
                to = toDate,
                compare = compare,
                error = error,
                onPreset = { days ->
                    to = today.toEpochDay()
                    from = today.minusDays(days - 1L).toEpochDay()
                },
                onEditFrom = { editing = EDIT_FROM },
                onEditTo = { editing = EDIT_TO },
                onCompare = { compare = it },
            )
        },
        confirmButton = {
            TextButton(onClick = { onApply(fromDate, toDate, compare) }, enabled = error == null, modifier = Modifier.testTag(TAG_APPLY)) {
                Text(stringResource(R.string.feature_insights_picker_apply))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_insights_picker_cancel)) } },
    )
    editing?.let { field ->
        DateDialog(
            initial = if (field == EDIT_FROM) fromDate else toDate,
            today = today,
            onPicked = { picked ->
                if (field == EDIT_FROM) from = picked.toEpochDay() else to = picked.toEpochDay()
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Suppress("LongParameterList") // Stateless content: one callback per control.
@Composable
internal fun RangePickerContent(
    from: LocalDate,
    to: LocalDate,
    compare: Boolean,
    @StringRes error: Int?,
    onPreset: (Int) -> Unit,
    onEditFrom: () -> Unit,
    onEditTo: () -> Unit,
    onCompare: (Boolean) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRESETS.forEach { (days, label) ->
                SuggestionChip(onClick = { onPreset(days) }, label = { Text(stringResource(label)) })
            }
        }
        SettingsRow(stringResource(R.string.feature_insights_picker_from), summary = format.format(from), onClick = onEditFrom)
        SettingsRow(stringResource(R.string.feature_insights_picker_to), summary = format.format(to), onClick = onEditTo)
        SwitchRow(stringResource(R.string.feature_insights_picker_compare), compare, onCompare)
        if (error != null) {
            Text(stringResource(error), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, today: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val todayMillis = today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPicked(LocalDate.ofEpochDay(Math.floorDiv(it, MILLIS_PER_DAY))) } ?: onDismiss()
            }) { Text(stringResource(R.string.feature_insights_picker_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_insights_picker_cancel)) } },
    ) { DatePicker(state = state) }
}

private const val EDIT_FROM = "from"
private const val EDIT_TO = "to"
private const val MILLIS_PER_DAY = 86_400_000L
internal const val TAG_APPLY = "rangeApply"
private val PRESETS = listOf(
    7 to R.string.feature_insights_picker_last_7,
    30 to R.string.feature_insights_picker_last_30,
    90 to R.string.feature_insights_picker_last_90,
)
