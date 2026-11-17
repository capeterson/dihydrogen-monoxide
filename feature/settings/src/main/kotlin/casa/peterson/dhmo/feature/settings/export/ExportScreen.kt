package casa.peterson.dhmo.feature.settings.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.core.designsystem.component.DhmoScreen
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.designsystem.component.SectionCard
import casa.peterson.dhmo.core.designsystem.component.SettingsRow
import casa.peterson.dhmo.feature.settings.R

@Composable
internal fun ExportScreen(onBack: () -> Unit, viewModel: ExportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Which export the open document picker is for; survives process death with the picker.
    var pending by rememberSaveable { mutableStateOf<ExportKind?>(null) }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        pending?.let { viewModel.export(it, uri) }
        pending = null
    }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        viewModel.export(ExportKind.JSON, uri)
        pending = null
    }
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(resources.getString(message.textRes()))
        viewModel.messageShown()
    }
    ExportContent(
        state = state,
        onBack = onBack,
        onExport = { kind ->
            pending = kind
            val name = viewModel.suggestedName(kind)
            if (kind == ExportKind.JSON) jsonLauncher.launch(name) else csvLauncher.launch(name)
        },
        snackbarHost = { SnackbarHost(snackbar) },
    )
}

private fun ExportMessage.textRes(): Int = when (this) {
    ExportMessage.Failed -> R.string.feature_settings_export_failed

    is ExportMessage.Saved -> when (kind) {
        ExportKind.ENTRIES_CSV -> R.string.feature_settings_export_saved_entries
        ExportKind.DAILY_CSV -> R.string.feature_settings_export_saved_daily
        ExportKind.JSON -> R.string.feature_settings_export_saved_json
    }
}

@Composable
internal fun ExportContent(
    state: ExportUiState,
    onBack: () -> Unit,
    onExport: (ExportKind) -> Unit,
    snackbarHost: @Composable () -> Unit = {},
) {
    DhmoScreen(
        topBar = { DhmoTopBar(stringResource(R.string.feature_settings_export_title), onNavigateBack = onBack) },
        snackbarHost = snackbarHost,
    ) { padding ->
        Column(
            Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = stringResource(R.string.feature_settings_export_spreadsheet_section)) {
                ExportRow(
                    ExportKind.ENTRIES_CSV,
                    stringResource(R.string.feature_settings_export_entries),
                    stringResource(R.string.feature_settings_export_entries_summary),
                    state,
                    onExport,
                )
                ExportRow(
                    ExportKind.DAILY_CSV,
                    stringResource(R.string.feature_settings_export_daily),
                    stringResource(R.string.feature_settings_export_daily_summary),
                    state,
                    onExport,
                )
            }
            SectionCard(title = stringResource(R.string.feature_settings_export_full_section)) {
                ExportRow(
                    ExportKind.JSON,
                    stringResource(R.string.feature_settings_export_json),
                    stringResource(R.string.feature_settings_export_json_summary),
                    state,
                    onExport,
                )
            }
        }
    }
}

@Composable
private fun ExportRow(kind: ExportKind, title: String, summary: String, state: ExportUiState, onExport: (ExportKind) -> Unit) {
    val running = state.running == kind
    val runningText = stringResource(R.string.feature_settings_export_running)
    SettingsRow(
        title = title,
        summary = summary,
        onClick = if (state.running == null) ({ onExport(kind) }) else null,
        trailing = if (running) {
            { CircularProgressIndicator(Modifier.size(24.dp).semantics { contentDescription = runningText }) }
        } else {
            null
        },
    )
}
