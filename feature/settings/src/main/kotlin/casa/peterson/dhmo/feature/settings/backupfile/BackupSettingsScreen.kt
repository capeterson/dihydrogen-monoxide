@file:Suppress("UNUSED_PARAMETER") // the Drive slots are filled by Phase 4

package casa.peterson.dhmo.feature.settings.backupfile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.core.designsystem.component.DhmoScreen
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.designsystem.component.SectionCard
import casa.peterson.dhmo.core.designsystem.component.SettingsRow
import casa.peterson.dhmo.core.model.sync.RestoreMode
import casa.peterson.dhmo.feature.settings.R
import casa.peterson.dhmo.feature.settings.navigation.SettingsIntegrations
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun BackupSettingsScreen(
    onBack: () -> Unit,
    integrations: SettingsIntegrations,
    onOpenRestore: () -> Unit,
    viewModel: BackupFileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Any document: providers label .json.gz and .enc files inconsistently, and the codec detects the format itself.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), viewModel::onFilePicked)
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(context.getString(message.textRes()))
        viewModel.messageShown()
    }
    BackupFileContent(
        state = state,
        onBack = onBack,
        onPickFile = { picker.launch(arrayOf("*/*")) },
        onPassphrase = viewModel::submitPassphrase,
        onConfirm = viewModel::confirm,
        onCancel = viewModel::cancel,
        onUndo = viewModel::undoLastRestore,
        snackbarHost = { SnackbarHost(snackbar) },
    )
}

private fun RestoreMessage.textRes(): Int = when (this) {
    RestoreMessage.RESTORED -> R.string.feature_settings_backup_msg_restored
    RestoreMessage.MERGED -> R.string.feature_settings_backup_msg_merged
    RestoreMessage.UNDONE -> R.string.feature_settings_backup_msg_undone
    RestoreMessage.NOTHING_TO_UNDO -> R.string.feature_settings_backup_msg_nothing
    RestoreMessage.NOT_A_BACKUP -> R.string.feature_settings_backup_msg_not_backup
    RestoreMessage.CORRUPT -> R.string.feature_settings_backup_msg_corrupt
    RestoreMessage.NEWER_VERSION -> R.string.feature_settings_backup_msg_newer
    RestoreMessage.READ_FAILED -> R.string.feature_settings_backup_msg_read_failed
    RestoreMessage.RESTORE_FAILED -> R.string.feature_settings_backup_msg_failed
}

@Composable
internal fun BackupFileContent(
    state: BackupFileUiState,
    onBack: () -> Unit,
    onPickFile: () -> Unit,
    onPassphrase: (CharArray) -> Unit,
    onConfirm: (RestoreMode) -> Unit,
    onCancel: () -> Unit,
    onUndo: () -> Unit,
    snackbarHost: @Composable () -> Unit = {},
    zone: ZoneId = ZoneId.systemDefault(),
) {
    var confirmUndo by rememberSaveable { mutableStateOf(false) }
    val idle = state.step == RestoreStep.Idle
    DhmoScreen(
        topBar = { DhmoTopBar(stringResource(R.string.feature_settings_backup_settings_title), onNavigateBack = onBack) },
        snackbarHost = snackbarHost,
    ) { padding ->
        Column(
            Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = stringResource(R.string.feature_settings_backup_file_section)) {
                val working = stringResource(R.string.feature_settings_backup_working)
                SettingsRow(
                    title = stringResource(R.string.feature_settings_backup_file_restore),
                    summary = stringResource(R.string.feature_settings_backup_file_restore_summary),
                    onClick = if (idle) onPickFile else null,
                    trailing = if (state.step == RestoreStep.Reading || state.step == RestoreStep.Restoring) {
                        { CircularProgressIndicator(Modifier.size(24.dp).semantics { contentDescription = working }) }
                    } else {
                        null
                    },
                )
                if (state.canUndo) {
                    SettingsRow(
                        title = stringResource(R.string.feature_settings_backup_undo),
                        summary = stringResource(R.string.feature_settings_backup_undo_summary),
                        onClick = if (idle) ({ confirmUndo = true }) else null,
                    )
                }
            }
            Text(
                stringResource(R.string.feature_settings_backup_drive_later),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
    when (val step = state.step) {
        is RestoreStep.NeedsPassphrase -> PassphraseDialog(step, onPassphrase, onCancel)
        is RestoreStep.Confirm -> ConfirmDialog(step.summary, zone, onConfirm, onCancel)
        else -> Unit
    }
    if (confirmUndo) {
        AlertDialog(
            onDismissRequest = { confirmUndo = false },
            title = { Text(stringResource(R.string.feature_settings_backup_undo_confirm_title)) },
            text = { Text(stringResource(R.string.feature_settings_backup_undo_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUndo = false
                    onUndo()
                }) { Text(stringResource(R.string.feature_settings_backup_undo_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUndo = false }) { Text(stringResource(R.string.feature_settings_backup_cancel)) }
            },
        )
    }
}

@Composable
private fun PassphraseDialog(step: RestoreStep.NeedsPassphrase, onSubmit: (CharArray) -> Unit, onCancel: () -> Unit) {
    // Not saveable on purpose: a passphrase should not be written to the saved instance state.
    var text by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val error = when {
        step.wrongPassphrase -> stringResource(R.string.feature_settings_backup_passphrase_wrong)
        step.tooShort -> stringResource(R.string.feature_settings_backup_passphrase_short)
        else -> null
    }
    val submit = {
        onSubmit(text.toCharArray())
        text = ""
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.feature_settings_backup_passphrase_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.feature_settings_backup_passphrase_text))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.feature_settings_backup_passphrase_label)) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = { visible = !visible }) {
                            val label = if (visible) {
                                R.string.feature_settings_backup_passphrase_hide
                            } else {
                                R.string.feature_settings_backup_passphrase_show
                            }
                            Text(stringResource(label))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = text.isNotEmpty()) { Text(stringResource(R.string.feature_settings_backup_unlock)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.feature_settings_backup_cancel)) } },
    )
}

@Composable
private fun ConfirmDialog(summary: BackupSummary, zone: ZoneId, onConfirm: (RestoreMode) -> Unit, onCancel: () -> Unit) {
    val made = remember(summary.createdAt, zone) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(summary.createdAt.atZone(zone))
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.feature_settings_backup_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.feature_settings_backup_confirm_details, made, summary.deviceName, summary.appVersion))
                Text(pluralStringResource(R.plurals.feature_settings_backup_confirm_entries, summary.entryCount, summary.entryCount))
                Text(stringResource(R.string.feature_settings_backup_confirm_modes), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                TextButton(onClick = { onConfirm(RestoreMode.REPLACE) }) { Text(stringResource(R.string.feature_settings_backup_replace)) }
                TextButton(onClick = { onConfirm(RestoreMode.MERGE) }) { Text(stringResource(R.string.feature_settings_backup_merge)) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.feature_settings_backup_cancel)) }
            }
        },
    )
}
