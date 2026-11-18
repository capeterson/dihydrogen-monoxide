package casa.peterson.dhmo.sync.healthconnect.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.health.connect.client.PermissionController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.core.designsystem.component.DhmoScreen
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.designsystem.component.SectionCard
import casa.peterson.dhmo.core.designsystem.component.SwitchRow
import casa.peterson.dhmo.core.model.sync.HcAvailability
import casa.peterson.dhmo.core.model.sync.HcSetting
import casa.peterson.dhmo.core.model.sync.HcSyncState
import casa.peterson.dhmo.sync.healthconnect.R

/** The Health Connect settings screen, handed to `:feature:settings` by `:app` (it may not depend on `:sync:*`). */
@Composable
fun HealthConnectSettingsScreen(onBack: () -> Unit) = HealthConnectSettingsScreen(onBack, hiltViewModel())

@Composable
internal fun HealthConnectSettingsScreen(onBack: () -> Unit, viewModel: HealthConnectSettingsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<HcSetting?>(null) }
    val permission = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        pending?.let { viewModel.onPermissionResult(it, granted) }
        pending = null
    }
    val context = LocalContext.current
    val toggle: (HcSetting, Boolean) -> Unit = { setting, on ->
        if (on) {
            pending = setting
            permission.launch(viewModel.permissionsFor(setting))
        } else {
            viewModel.turnOff(setting)
        }
    }
    HealthConnectSettingsContent(
        state = state,
        onBack = onBack,
        onToggle = toggle,
        onSyncNow = viewModel::syncNow,
        onSourceToggle = viewModel::setSourceIncluded,
        onInstall = {
            val intent = Intent(Intent.ACTION_VIEW, INSTALL_URI.toUri()).setPackage("com.android.vending")
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                // No Play Store: nothing to open.
            }
        },
    )
}

private val ROWS = listOf(
    Triple(HcSetting.WRITE_HYDRATION, R.string.sync_healthconnect_write_title, R.string.sync_healthconnect_write_summary),
    Triple(HcSetting.IMPORT_HYDRATION, R.string.sync_healthconnect_import_title, R.string.sync_healthconnect_import_summary),
    Triple(HcSetting.SYNC_WEIGHT, R.string.sync_healthconnect_weight_title, R.string.sync_healthconnect_weight_summary),
    Triple(HcSetting.USE_EXERCISE, R.string.sync_healthconnect_exercise_title, R.string.sync_healthconnect_exercise_summary),
    Triple(HcSetting.READ_STEPS_AND_CALORIES, R.string.sync_healthconnect_steps_title, R.string.sync_healthconnect_steps_summary),
)

@Composable
internal fun HealthConnectSettingsContent(
    state: HcUiState,
    onBack: () -> Unit,
    onToggle: (HcSetting, Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onSourceToggle: (String, Boolean) -> Unit,
    onInstall: () -> Unit,
) {
    DhmoScreen(topBar = { DhmoTopBar(stringResource(R.string.sync_healthconnect_title), onNavigateBack = onBack) }) { padding ->
        Column(
            Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state.availability) {
                HcAvailability.UNAVAILABLE -> Text(stringResource(R.string.sync_healthconnect_unavailable))

                HcAvailability.PROVIDER_UPDATE_REQUIRED -> {
                    Text(stringResource(R.string.sync_healthconnect_update_required))
                    Button(onClick = onInstall) { Text(stringResource(R.string.sync_healthconnect_update_action)) }
                }

                HcAvailability.AVAILABLE -> Available(state, onToggle, onSyncNow, onSourceToggle)
            }
        }
    }
}

@Composable
private fun Available(
    state: HcUiState,
    onToggle: (HcSetting, Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onSourceToggle: (String, Boolean) -> Unit,
) {
    Text(stringResource(R.string.sync_healthconnect_intro), style = MaterialTheme.typography.bodyMedium)
    SectionCard {
        ROWS.forEach { (setting, title, summary) ->
            SwitchRow(
                stringResource(title),
                checked = isOn(state, setting),
                onCheckedChange = { onToggle(setting, it) },
                summary = stringResource(summary),
            )
        }
        if (state.backgroundOffered) {
            SwitchRow(
                stringResource(R.string.sync_healthconnect_background_title),
                checked = state.settings.backgroundSync,
                onCheckedChange = { onToggle(HcSetting.BACKGROUND_SYNC, it) },
                summary = stringResource(R.string.sync_healthconnect_background_summary),
            )
        }
        if (state.historyOffered) {
            SwitchRow(
                stringResource(R.string.sync_healthconnect_history_title),
                checked = state.settings.importHistory,
                onCheckedChange = { onToggle(HcSetting.IMPORT_HISTORY, it) },
                summary = stringResource(R.string.sync_healthconnect_history_summary),
            )
        }
    }
    if (state.permissionDenied) {
        Text(stringResource(R.string.sync_healthconnect_permission_denied), color = MaterialTheme.colorScheme.error)
    }
    if (state.settings.importHydration) {
        SectionCard(title = stringResource(R.string.sync_healthconnect_sources_title)) {
            if (state.sources.isEmpty()) {
                Text(stringResource(R.string.sync_healthconnect_sources_empty), modifier = Modifier.padding(16.dp))
            } else {
                state.sources.forEach { row ->
                    SwitchRow(row.label, checked = row.included, onCheckedChange = { onSourceToggle(row.packageName, it) })
                }
            }
        }
    }
    Text(statusText(state.sync), style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = onSyncNow, enabled = state.sync !is HcSyncState.Syncing && state.sync !is HcSyncState.Disabled) {
        Text(stringResource(R.string.sync_healthconnect_sync_now))
    }
}

private fun isOn(state: HcUiState, setting: HcSetting): Boolean = when (setting) {
    HcSetting.WRITE_HYDRATION -> state.settings.writeHydration
    HcSetting.IMPORT_HYDRATION -> state.settings.importHydration
    HcSetting.SYNC_WEIGHT -> state.settings.syncWeight
    HcSetting.USE_EXERCISE -> state.settings.useExercise
    HcSetting.READ_STEPS_AND_CALORIES -> state.settings.readStepsAndCalories
    HcSetting.BACKGROUND_SYNC -> state.settings.backgroundSync
    HcSetting.IMPORT_HISTORY -> state.settings.importHistory
}

@Composable
private fun statusText(sync: HcSyncState): String = when (sync) {
    HcSyncState.Syncing -> stringResource(R.string.sync_healthconnect_status_syncing)

    is HcSyncState.NeedsPermission -> stringResource(R.string.sync_healthconnect_status_needs_permission)

    is HcSyncState.Error -> stringResource(R.string.sync_healthconnect_status_error)

    is HcSyncState.Idle -> if (sync.pendingOps > 0) {
        pluralStringResource(R.plurals.sync_healthconnect_status_idle_pending, sync.pendingOps, sync.pendingOps)
    } else if (sync.lastSyncAt == null) {
        stringResource(R.string.sync_healthconnect_status_never)
    } else {
        stringResource(R.string.sync_healthconnect_status_idle)
    }

    else -> ""
}

private const val INSTALL_URI = "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding"
