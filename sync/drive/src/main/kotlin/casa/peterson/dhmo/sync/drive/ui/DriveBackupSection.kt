package casa.peterson.dhmo.sync.drive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.core.designsystem.component.SectionCard
import casa.peterson.dhmo.core.designsystem.component.SwitchRow
import casa.peterson.dhmo.core.model.settings.BackupFrequency
import casa.peterson.dhmo.core.model.sync.BackupError
import casa.peterson.dhmo.core.model.sync.DriveBackupState
import casa.peterson.dhmo.sync.drive.R
import casa.peterson.dhmo.sync.drive.auth.rememberDriveAuthLauncher
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The Google Drive section of the Backup screen, handed to `:feature:settings` by `:app`. */
@Composable
fun DriveBackupSection(onOpenRestore: () -> Unit) = DriveBackupSection(onOpenRestore, hiltViewModel())

@Composable
internal fun DriveBackupSection(onOpenRestore: () -> Unit, viewModel: DriveBackupViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launcher = rememberDriveAuthLauncher(viewModel::onAuthResult)
    DriveBackupContent(
        state = state,
        onConnect = launcher::launch,
        onDisconnect = viewModel::disconnect,
        onAutomatic = viewModel::setAutomatic,
        onFrequency = viewModel::setFrequency,
        onUnmetered = viewModel::setUnmeteredOnly,
        onEnableEncryption = viewModel::enableEncryption,
        onDisableEncryption = viewModel::disableEncryption,
        onBackUpNow = viewModel::backUpNow,
        onOpenRestore = onOpenRestore,
    )
}

@Composable
internal fun DriveBackupContent(
    state: DriveBackupUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onAutomatic: (Boolean) -> Unit,
    onFrequency: (BackupFrequency) -> Unit,
    onUnmetered: (Boolean) -> Unit,
    onEnableEncryption: (CharArray) -> Unit,
    onDisableEncryption: () -> Unit,
    onBackUpNow: () -> Unit,
    onOpenRestore: () -> Unit,
) {
    var askPassphrase by rememberSaveable { mutableStateOf(false) }
    SectionCard(title = stringResource(R.string.drive_section_title)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (val drive = state.drive) {
                DriveBackupState.NotConfigured -> Text(
                    stringResource(R.string.drive_not_configured),
                    style = MaterialTheme.typography.bodyMedium,
                )

                DriveBackupState.SignedOut -> {
                    Text(stringResource(R.string.drive_intro), style = MaterialTheme.typography.bodyMedium)
                    if (state.connectFailed) Text(stringResource(R.string.drive_connect_failed), color = MaterialTheme.colorScheme.error)
                    Button(onClick = onConnect) { Text(stringResource(R.string.drive_connect)) }
                }

                DriveBackupState.NeedsReconnect -> {
                    Text(stringResource(R.string.drive_needs_reconnect), color = MaterialTheme.colorScheme.error)
                    Button(onClick = onConnect) { Text(stringResource(R.string.drive_reconnect_action)) }
                    TextButton(onClick = onDisconnect) { Text(stringResource(R.string.drive_disconnect)) }
                }

                else -> Connected(state, drive, onDisconnect, onAutomatic, onFrequency, onUnmetered, {
                    askPassphrase = true
                }, onDisableEncryption, onBackUpNow, onOpenRestore)
            }
        }
    }
    if (askPassphrase) {
        PassphraseSetupDialog(onDismiss = { askPassphrase = false }, onConfirm = {
            askPassphrase = false
            onEnableEncryption(it)
        })
    }
}

@Composable
private fun Connected(
    state: DriveBackupUiState,
    drive: DriveBackupState,
    onDisconnect: () -> Unit,
    onAutomatic: (Boolean) -> Unit,
    onFrequency: (BackupFrequency) -> Unit,
    onUnmetered: (Boolean) -> Unit,
    onEncryptOn: () -> Unit,
    onEncryptOff: () -> Unit,
    onBackUpNow: () -> Unit,
    onOpenRestore: () -> Unit,
) {
    state.email?.let { Text(stringResource(R.string.drive_connected_as, it), style = MaterialTheme.typography.bodyMedium) }
    SwitchRow(stringResource(R.string.drive_auto), checked = state.settings.driveEnabled, onCheckedChange = onAutomatic)
    if (state.settings.driveEnabled) {
        Column(Modifier.fillMaxWidth()) {
            FrequencyChoice(R.string.drive_frequency_daily, state.settings.frequency == BackupFrequency.DAILY) {
                onFrequency(BackupFrequency.DAILY)
            }
            FrequencyChoice(R.string.drive_frequency_weekly, state.settings.frequency == BackupFrequency.WEEKLY) {
                onFrequency(BackupFrequency.WEEKLY)
            }
        }
        SwitchRow(
            stringResource(R.string.drive_unmetered),
            checked = state.settings.unmeteredOnly,
            onCheckedChange = onUnmetered,
            summary = stringResource(R.string.drive_unmetered_summary),
        )
    }
    SwitchRow(
        stringResource(R.string.drive_encrypt),
        checked = state.settings.encryptionEnabled,
        onCheckedChange = { if (it) onEncryptOn() else onEncryptOff() },
        summary = stringResource(R.string.drive_encrypt_summary),
    )
    Text(statusText(drive), style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onBackUpNow, enabled = drive !is DriveBackupState.Running) { Text(stringResource(R.string.drive_backup_now)) }
        OutlinedButton(onClick = onOpenRestore) { Text(stringResource(R.string.drive_restore)) }
    }
    TextButton(onClick = onDisconnect) { Text(stringResource(R.string.drive_disconnect)) }
}

@Composable
private fun FrequencyChoice(label: Int, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = null)
        Text(stringResource(label), modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun statusText(drive: DriveBackupState): String = when (drive) {
    DriveBackupState.Running -> stringResource(R.string.drive_running)

    is DriveBackupState.Error -> stringResource(
        when (drive.error) {
            BackupError.QUOTA -> R.string.drive_error_quota
            BackupError.NETWORK -> R.string.drive_error_network
            else -> R.string.drive_error_other
        },
    )

    is DriveBackupState.Ready -> drive.lastSuccessAt?.let { stringResource(R.string.drive_last_backup, formatTime(it)) }
        ?: stringResource(R.string.drive_never_backed_up)

    else -> ""
}

internal fun formatTime(instant: Instant): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(instant.atZone(ZoneId.systemDefault()))

@Composable
private fun PassphraseSetupDialog(onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    var first by rememberSaveable { mutableStateOf("") }
    var second by rememberSaveable { mutableStateOf("") }
    var understood by rememberSaveable { mutableStateOf(false) }
    val tooShort = first.isNotEmpty() && first.length < MIN_PASSPHRASE
    val mismatch = second.isNotEmpty() && first != second
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.drive_encrypt_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.drive_encrypt_text))
                OutlinedTextField(
                    first,
                    { first = it },
                    label = { Text(stringResource(R.string.drive_passphrase)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = tooShort,
                    supportingText = { if (tooShort) Text(stringResource(R.string.drive_passphrase_short)) },
                )
                OutlinedTextField(
                    second,
                    { second = it },
                    label = { Text(stringResource(R.string.drive_passphrase_confirm)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = mismatch,
                    supportingText = { if (mismatch) Text(stringResource(R.string.drive_passphrase_mismatch)) },
                )
                Row(
                    Modifier.selectable(understood, role = Role.Checkbox) {
                        understood = !understood
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = understood, onCheckedChange = null)
                    Text(stringResource(R.string.drive_encrypt_acknowledge), modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(first.toCharArray()) },
                enabled =
                    first.length >= MIN_PASSPHRASE && first == second && understood,
            ) {
                Text(stringResource(R.string.drive_encrypt_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.drive_cancel)) } },
    )
}

private const val MIN_PASSPHRASE = 8
