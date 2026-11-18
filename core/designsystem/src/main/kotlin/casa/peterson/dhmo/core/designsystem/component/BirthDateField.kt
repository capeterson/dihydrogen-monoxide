package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import casa.peterson.dhmo.core.designsystem.R
import casa.peterson.dhmo.core.designsystem.icon.DhmoIcons
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val OLDEST_YEAR = 1900
private const val YEARS_AGO_TO_OPEN_ON = 30L

/**
 * A birth date chosen from a calendar. [value] is an ISO date (`1990-05-10`) or empty; that is only how it is passed
 * around. The text shown follows the device's language and region (`10 May 1990`, `May 10, 1990`, `10. Mai 1990`),
 * and so do the calendar's own labels and its typed-entry mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthDateField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    label: String = stringResource(R.string.core_designsystem_birth_date),
) {
    val locale = LocalConfiguration.current.locales[0]
    val date = runCatching { LocalDate.parse(value) }.getOrNull()
    val shown = date?.let { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(it) }.orEmpty()
    var open by rememberSaveable { mutableStateOf(false) }
    val choose = stringResource(R.string.core_designsystem_birth_date_choose)
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = shown,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(painterResource(DhmoIcons.Calendar), contentDescription = null) },
            isError = isError,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // A read-only text field swallows taps, so a transparent layer on top opens the calendar instead.
        Box(Modifier.matchParentSize().clickable(onClickLabel = choose, role = Role.Button) { open = true })
    }
    if (open) {
        val today = LocalDate.now()
        val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            initialDisplayedMonthMillis = (
                date ?: today.minusYears(
                    YEARS_AGO_TO_OPEN_ON,
                )
                ).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = OLDEST_YEAR..today.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis

                override fun isSelectableYear(year: Int): Boolean = year in OLDEST_YEAR..today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            onValueChange(Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate().toString())
                        }
                        open = false
                    },
                    enabled = state.selectedDateMillis != null,
                ) { Text(stringResource(R.string.core_designsystem_date_ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.core_designsystem_date_cancel)) } },
        ) { DatePicker(state) }
    }
}
