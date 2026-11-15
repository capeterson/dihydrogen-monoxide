package casa.peterson.dhmo.core.designsystem.component

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import casa.peterson.dhmo.core.designsystem.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val MINUTES_PER_HOUR = 60

/** [minutesOfDay] (0..1439) formatted as a short local time for [locale], e.g. `7:30 AM` or `07:30`. */
internal fun formatTimeOfDay(minutesOfDay: Int, locale: java.util.Locale): String =
    LocalTime.of(minutesOfDay / MINUTES_PER_HOUR, minutesOfDay % MINUTES_PER_HOUR)
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

/**
 * A settings-style row with a [label] and the chosen time; tapping it opens a time picker dialog.
 * The time is [minutesOfDay] (0..1439) and is reported back the same way.
 */
@Composable
fun TimePickerRow(label: String, minutesOfDay: Int, onTimeSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val timeText = formatTimeOfDay(minutesOfDay, currentLocale())
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .clickable(
                    onClickLabel = stringResource(R.string.core_designsystem_time_row_action, label),
                    role = Role.Button,
                    onClick = { showPicker = true },
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(text = timeText, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
    if (showPicker) {
        val state =
            rememberTimePickerState(
                initialHour = minutesOfDay / MINUTES_PER_HOUR,
                initialMinute = minutesOfDay % MINUTES_PER_HOUR,
                is24Hour = DateFormat.is24HourFormat(LocalContext.current),
            )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(label) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    showPicker = false
                    onTimeSelected(state.hour * MINUTES_PER_HOUR + state.minute)
                }) { Text(stringResource(R.string.core_designsystem_ok)) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.core_designsystem_cancel)) } },
        )
    }
}
