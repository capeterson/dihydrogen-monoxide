package casa.peterson.dhmo.feature.history

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

@Composable
internal fun HistoryDayScreen(
    day: LocalDate,
    onBack: () -> Unit,
    onEditEntry: (UUID) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val locale = LocalConfiguration.current.locales[0]
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dayFlow = remember(day, viewModel) { viewModel.entriesForDay(day) }
    val entries by dayFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val snackbar = remember { SnackbarHostState() }
    HistoryEffects(viewModel.effects, snackbar, viewModel::undoDelete)
    Scaffold(
        topBar = {
            DhmoTopBar(day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)), onNavigateBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                HistoryDayHeader(
                    HistoryItem.DayHeader(
                        day,
                        state.totals[day]?.totalMl ?: 0,
                        state.goals[day],
                        day in state.anomalies,
                        state.totals[day]?.entryCount ?: 0,
                    ),
                    state.units,
                )
            }
            if (entries.isEmpty()) item { Text(stringResource(R.string.feature_history_day_empty), Modifier.padding(16.dp)) }
            items(entries, key = { it.id }) { entry ->
                HistoryEntryRow(
                    entry,
                    state.units,
                    viewModel.sourceLabel(entry.hcDataOrigin),
                    onEdit = { onEditEntry(entry.id) },
                    onDelete = { viewModel.deleteEntry(entry.id) },
                    onAdopt = { viewModel.adopt(entry.id) },
                )
            }
        }
    }
}
