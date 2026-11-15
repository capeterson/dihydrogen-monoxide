package casa.peterson.dhmo.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.core.designsystem.component.TimePickerRow
import casa.peterson.dhmo.core.model.IntakeEntry
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditEntryScreen(onDismiss: () -> Unit, viewModel: EditEntryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.completedEvents.collect { onDismiss() } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        when (val current = state) {
            EditEntryState.Loading -> Unit
            EditEntryState.Missing -> Text(stringResource(R.string.feature_today_entry_missing), Modifier.padding(24.dp))
            is EditEntryState.Ready -> EditEntryContent(current, viewModel::save)
        }
    }
}

@Composable
internal fun EditEntryContent(state: EditEntryState.Ready, save: (Int, java.time.Instant, String?) -> Unit) {
    val entry = state.entry
    val local = entry.instant.atOffset(entry.zoneOffset)
    var amount by rememberSaveable(entry.id.toString()) { mutableStateOf(entry.volumeMl.toString()) }
    var date by rememberSaveable(entry.id.toString()) { mutableStateOf(local.toLocalDate().toString()) }
    var minutes by rememberSaveable(entry.id.toString()) { mutableStateOf(local.hour * 60 + local.minute) }
    var note by rememberSaveable(entry.id.toString()) { mutableStateOf(entry.note.orEmpty()) }
    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
    val parsedAmount = amount.toIntOrNull()
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.feature_today_edit_entry_title))
        if (!entry.isEditable) Text(stringResource(R.string.feature_today_entry_not_editable))
        OutlinedTextField(amount, { amount = it }, label = { Text(stringResource(R.string.feature_today_volume)) }, singleLine = true)
        OutlinedTextField(date, { date = it }, label = { Text(stringResource(R.string.feature_today_date)) }, singleLine = true)
        TimePickerRow(stringResource(R.string.feature_today_time_label), minutes, { minutes = it })
        OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.feature_today_note)) })
        state.error?.let { Text(stringResource(it)) }
        Button(
            enabled = entry.isEditable && parsedDate != null && parsedAmount != null,
            onClick = {
                val instant = requireNotNull(parsedDate).atStartOfDay().plusMinutes(minutes.toLong()).toInstant(entry.zoneOffset)
                save(requireNotNull(parsedAmount), instant, note.ifBlank { null })
            },
        ) { Text(stringResource(R.string.feature_today_save)) }
    }
}
