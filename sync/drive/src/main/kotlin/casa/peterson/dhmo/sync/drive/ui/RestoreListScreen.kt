package casa.peterson.dhmo.sync.drive.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.core.designsystem.component.DhmoScreen
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.model.sync.RemoteBackup
import casa.peterson.dhmo.core.model.sync.RestoreMode
import casa.peterson.dhmo.sync.drive.R
import casa.peterson.dhmo.sync.drive.auth.rememberDriveAuthLauncher

/** Settings → Backup → Restore from Google Drive. */
@Composable
fun DriveRestoreScreen(onBack: () -> Unit) {
    val viewModel: RestoreListViewModel = hiltViewModel()
    DhmoScreen(topBar = { DhmoTopBar(stringResource(R.string.restore_title), onNavigateBack = onBack) }) { padding ->
        RestoreList(viewModel, Modifier.padding(padding).consumeWindowInsets(padding), onFinished = {})
    }
}

/**
 * Onboarding on a new phone: sign in, and if there are backups, restore one. Skipping continues the normal setup;
 * a restore finishes onboarding straight away.
 */
@Composable
fun DriveRestoreOffer(onSkip: () -> Unit, onRestored: () -> Unit) {
    var connected by rememberSaveable { mutableStateOf(false) }
    var failed by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberDriveAuthLauncher { ok ->
        connected = ok
        failed = !ok
    }
    DhmoScreen { padding ->
        if (!connected) {
            Column(Modifier.padding(padding).fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.restore_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.drive_intro), style = MaterialTheme.typography.bodyLarge)
                if (failed) Text(stringResource(R.string.drive_connect_failed), color = MaterialTheme.colorScheme.error)
                Button(onClick = launcher::launch, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.drive_connect)) }
                OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.drive_cancel)) }
            }
        } else {
            val viewModel: RestoreListViewModel = hiltViewModel()
            Column(Modifier.padding(padding).fillMaxSize()) {
                RestoreList(viewModel, Modifier.weight(1f), onFinished = {
                    viewModel.markOnboardingComplete()
                    onRestored()
                })
                TextButton(onClick = onSkip, modifier = Modifier.padding(16.dp)) { Text(stringResource(R.string.drive_cancel)) }
            }
        }
    }
}

@Composable
private fun RestoreList(viewModel: RestoreListViewModel, modifier: Modifier, onFinished: () -> Unit) {
    val list by viewModel.list.collectAsStateWithLifecycle()
    val step by viewModel.step.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize()) {
        when (val ui = list) {
            RestoreUi.Loading -> Centered { CircularProgressIndicator() }
            is RestoreUi.Failed -> Centered {
                Text(stringResource(if (ui.reason == ListFailure.AUTH) R.string.restore_error_auth else R.string.restore_error_network))
                OutlinedButton(onClick = viewModel::load) { Text(stringResource(R.string.restore_retry)) }
            }

            is RestoreUi.Loaded -> if (ui.backups.isEmpty()) {
                Centered { Text(stringResource(R.string.restore_empty)) }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(ui.backups, key = { it.id }) { backup -> BackupRow(backup) { viewModel.select(backup) } }
                }
            }
        }
    }
    RestoreDialogs(step, viewModel, onFinished)
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp, alignment = androidx.compose.ui.Alignment.CenterVertically)) {
        content()
    }
}

@Composable
private fun BackupRow(backup: RemoteBackup, onClick: () -> Unit) {
    val disabled = backup.needsNewerApp()
    val details = buildList {
        backup.entryCount?.let { add(pluralStringResource(R.plurals.restore_entries, it, it)) }
        if (backup.encrypted) add(stringResource(R.string.restore_encrypted))
        if (disabled) add(stringResource(R.string.restore_needs_newer_app))
    }.joinToString(" · ")
    ListItem(
        headlineContent = { Text(formatTime(backup.createdAt)) },
        supportingContent = { Text(details) },
        modifier = Modifier.fillMaxWidth().let { if (disabled) it else it.clickable(onClick = onClick) },
    )
}

@Composable
private fun RestoreDialogs(step: RestoreStep, viewModel: RestoreListViewModel, onFinished: () -> Unit) {
    when (step) {
        RestoreStep.None -> Unit
        is RestoreStep.Passphrase -> PassphraseDialog(step, viewModel::submitPassphrase, viewModel::dismiss)
        is RestoreStep.Confirm -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_text)) },
            confirmButton = { TextButton(onClick = { viewModel.restore(RestoreMode.REPLACE) }) { Text(stringResource(R.string.restore_replace)) } },
            dismissButton = {
                Column {
                    TextButton(onClick = { viewModel.restore(RestoreMode.MERGE) }) { Text(stringResource(R.string.restore_merge)) }
                    TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.drive_cancel)) }
                }
            },
        )

        is RestoreStep.Restoring -> AlertDialog(onDismissRequest = {}, confirmButton = {}, text = { CircularProgressIndicator() })
        is RestoreStep.Done -> AlertDialog(
            onDismissRequest = {},
            text = { Text(stringResource(if (step.mode == RestoreMode.REPLACE) R.string.restore_done_replaced else R.string.restore_done_merged)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismiss()
                    onFinished()
                }) { Text("OK") }
            },
        )

        RestoreStep.Failed -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            text = { Text(stringResource(R.string.restore_failed)) },
            confirmButton = { TextButton(onClick = viewModel::dismiss) { Text("OK") } },
        )
    }
}

@Composable
private fun PassphraseDialog(step: RestoreStep.Passphrase, onSubmit: (RemoteBackup, CharArray) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_passphrase_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.restore_passphrase_text))
                OutlinedTextField(
                    text,
                    { text = it },
                    label = { Text(stringResource(R.string.drive_passphrase)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = step.wrong,
                    supportingText = { if (step.wrong) Text(stringResource(R.string.restore_wrong_passphrase)) },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(step.backup, text.toCharArray()) }, enabled = text.isNotEmpty()) { Text(stringResource(R.string.restore_unlock)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.drive_cancel)) } },
    )
}
