package casa.peterson.dhmo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.R
import casa.peterson.dhmo.core.data.repository.InstallResult
import casa.peterson.dhmo.core.data.repository.UpdateFailure
import casa.peterson.dhmo.core.data.repository.UpdateStatus

/**
 * Offers a newer release: "Update" downloads and checks it, then hands it to Android's installer in place. Shown only
 * when the opt-in updater found something; "Not now" hides that release until a newer one is published.
 */
@Composable
fun UpdateOfferDialog(viewModel: UpdateOfferViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val installResult by viewModel.installResult.collectAsStateWithLifecycle()
    val downloadStarted by viewModel.downloadStarted.collectAsStateWithLifecycle()
    val hidden by viewModel.hidden.collectAsStateWithLifecycle()
    LaunchedEffect(status) {
        if (status is UpdateStatus.Checking) viewModel.show()
    }
    if (hidden) return
    when (val current = status) {
        is UpdateStatus.Available -> OfferDialog(
            title = stringResource(R.string.update_offer_title),
            confirm = stringResource(R.string.update_offer_update),
            onConfirm = viewModel::download,
            dismiss = stringResource(R.string.update_offer_not_now),
            onDismiss = viewModel::notNow,
        ) {
            Text(stringResource(R.string.update_offer_body, current.update.name))
            if (current.update.notes.isNotEmpty()) Text(current.update.notes, style = MaterialTheme.typography.bodySmall)
        }

        is UpdateStatus.Downloading -> OfferDialog(
            title = stringResource(R.string.update_offer_title),
            dismiss = stringResource(R.string.update_offer_close),
            onDismiss = viewModel::hide,
        ) {
            Text(stringResource(R.string.update_offer_downloading, current.update.name))
            current.percent?.let { LinearProgressIndicator(progress = { it / PERCENT }, modifier = Modifier.fillMaxWidth()) }
                ?: LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        is UpdateStatus.ReadyToInstall -> OfferDialog(
            title = stringResource(R.string.update_offer_title),
            confirm = stringResource(R.string.update_offer_install),
            onConfirm = viewModel::install,
            dismiss = stringResource(R.string.update_offer_not_now),
            onDismiss = viewModel::notNow,
        ) {
            Text(stringResource(R.string.update_offer_ready, current.update.name))
            when (installResult) {
                InstallResult.NEEDS_PERMISSION -> Text(stringResource(R.string.update_offer_permission))
                InstallResult.NOTHING_TO_INSTALL -> Text(stringResource(R.string.update_offer_install_error))
                else -> Unit
            }
        }

        is UpdateStatus.Failed -> if (downloadStarted) {
            OfferDialog(
                title = stringResource(R.string.update_offer_title),
                confirm = stringResource(R.string.update_offer_retry),
                onConfirm = viewModel::download,
                dismiss = stringResource(R.string.update_offer_close),
                onDismiss = viewModel::hide,
            ) {
                Text(stringResource(offerFailureText(current.reason)), color = MaterialTheme.colorScheme.error)
            }
        }

        else -> Unit
    }
}

@Composable
private fun OfferDialog(
    title: String,
    confirm: String? = null,
    onConfirm: () -> Unit = {},
    dismiss: String? = null,
    onDismiss: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } },
        confirmButton = { confirm?.let { TextButton(onClick = onConfirm) { Text(it) } } },
        dismissButton = { dismiss?.let { TextButton(onClick = onDismiss) { Text(it) } } },
    )
}

private fun offerFailureText(reason: UpdateFailure): Int = when (reason) {
    UpdateFailure.UNSUPPORTED -> R.string.update_offer_error_unsupported
    UpdateFailure.NETWORK -> R.string.update_offer_error_network
    UpdateFailure.HTTP, UpdateFailure.PARSE -> R.string.update_offer_error_server
    UpdateFailure.NO_APK -> R.string.update_offer_error_no_apk
    UpdateFailure.DOWNLOAD -> R.string.update_offer_error_download
    UpdateFailure.CHECKSUM -> R.string.update_offer_error_checksum
    UpdateFailure.WRONG_SIGNATURE -> R.string.update_offer_error_signature
}

private const val PERCENT = 100f
