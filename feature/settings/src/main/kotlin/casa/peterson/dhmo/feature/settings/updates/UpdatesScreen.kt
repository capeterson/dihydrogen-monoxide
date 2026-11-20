package casa.peterson.dhmo.feature.settings.updates

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.core.data.repository.UpdateFailure
import casa.peterson.dhmo.core.data.repository.UpdateStatus
import casa.peterson.dhmo.core.designsystem.component.DhmoScreen
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.designsystem.component.SectionCard
import casa.peterson.dhmo.core.designsystem.component.SwitchRow
import casa.peterson.dhmo.feature.settings.R

@Composable
internal fun UpdatesScreen(onBack: () -> Unit, viewModel: UpdatesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UpdatesContent(
        state = state,
        onBack = onBack,
        onEnabledChange = viewModel::setEnabled,
        onCheckNow = viewModel::checkNow,
        onDownload = viewModel::download,
        onInstall = viewModel::install,
        onDismiss = viewModel::dismiss,
    )
}

@Suppress("LongParameterList") // One callback per button; grouping them would only move the list.
@Composable
internal fun UpdatesContent(
    state: UpdatesUiState,
    onBack: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onCheckNow: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    DhmoScreen(topBar = { DhmoTopBar(stringResource(R.string.feature_settings_updates_title), onNavigateBack = onBack) }) { padding ->
        Column(
            Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.feature_settings_updates_intro), style = MaterialTheme.typography.bodyMedium)
            SectionCard {
                SwitchRow(
                    stringResource(R.string.feature_settings_updates_enable),
                    checked = state.enabled,
                    onCheckedChange = onEnabledChange,
                    summary = stringResource(R.string.feature_settings_updates_enable_summary),
                )
            }
            if (state.enabled) {
                StatusCard(state, onCheckNow, onDownload, onInstall, onDismiss)
            }
        }
    }
}

@Composable
private fun StatusCard(
    state: UpdatesUiState,
    onCheckNow: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.feature_settings_updates_status)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (val status = state.status) {
                UpdateStatus.Idle -> Text(stringResource(R.string.feature_settings_updates_status_idle))

                UpdateStatus.Checking -> {
                    Text(stringResource(R.string.feature_settings_updates_status_checking))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                UpdateStatus.UpToDate -> Text(stringResource(R.string.feature_settings_updates_status_current))

                is UpdateStatus.Available -> {
                    Text(stringResource(R.string.feature_settings_updates_status_available, status.update.name))
                    if (status.update.notes.isNotEmpty()) {
                        Text(
                            status.update.notes,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = NOTES_LINES,
                        )
                    }
                    Button(onClick = onDownload) { Text(stringResource(R.string.feature_settings_updates_download)) }
                    OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.feature_settings_updates_skip)) }
                }

                is UpdateStatus.Downloading -> {
                    Text(stringResource(R.string.feature_settings_updates_status_downloading, status.update.name))
                    status.percent?.let { LinearProgressIndicator(progress = { it / PERCENT }, modifier = Modifier.fillMaxWidth()) }
                        ?: LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                is UpdateStatus.ReadyToInstall -> {
                    Text(stringResource(R.string.feature_settings_updates_status_ready, status.update.name))
                    if (state.needsInstallPermission) {
                        Text(stringResource(R.string.feature_settings_updates_install_permission), color = MaterialTheme.colorScheme.error)
                    }
                    if (state.installFailed) {
                        Text(stringResource(R.string.feature_settings_updates_install_error), color = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = onInstall) { Text(stringResource(R.string.feature_settings_updates_install)) }
                }

                is UpdateStatus.Failed -> {
                    Text(stringResource(failureText(status.reason)), color = MaterialTheme.colorScheme.error)
                    if (status.reason == UpdateFailure.DOWNLOAD || status.reason == UpdateFailure.CHECKSUM) {
                        OutlinedButton(onClick = onDownload) { Text(stringResource(R.string.feature_settings_updates_retry_download)) }
                    }
                }
            }
            state.lastCheckAtMs?.let {
                Text(
                    stringResource(R.string.feature_settings_updates_last_check, DateUtils.getRelativeTimeSpanString(it).toString()),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val busy = state.status is UpdateStatus.Checking || state.status is UpdateStatus.Downloading
            OutlinedButton(onClick = onCheckNow, enabled = !busy) { Text(stringResource(R.string.feature_settings_updates_check_now)) }
        }
    }
}

@StringRes
internal fun failureText(reason: UpdateFailure): Int = when (reason) {
    UpdateFailure.UNSUPPORTED -> R.string.feature_settings_updates_error_unsupported
    UpdateFailure.NETWORK -> R.string.feature_settings_updates_error_network
    UpdateFailure.HTTP, UpdateFailure.PARSE -> R.string.feature_settings_updates_error_server
    UpdateFailure.NO_APK -> R.string.feature_settings_updates_error_no_apk
    UpdateFailure.DOWNLOAD -> R.string.feature_settings_updates_error_download
    UpdateFailure.CHECKSUM -> R.string.feature_settings_updates_error_checksum
    UpdateFailure.WRONG_SIGNATURE -> R.string.feature_settings_updates_error_signature
}

private const val NOTES_LINES = 6
private const val PERCENT = 100f
