package casa.peterson.dhmo.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import casa.peterson.dhmo.core.designsystem.component.DayResultBadge
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.designsystem.component.EmptyState
import casa.peterson.dhmo.core.designsystem.component.VolumeText
import casa.peterson.dhmo.core.designsystem.icon.DhmoIcons
import casa.peterson.dhmo.core.domain.format.VolumeFormatter
import casa.peterson.dhmo.core.domain.stats.DayResult
import casa.peterson.dhmo.core.domain.stats.DayStat
import casa.peterson.dhmo.core.domain.stats.dayResult
import casa.peterson.dhmo.core.model.IntakeEntry
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Composable
internal fun HistoryScreen(onOpenDay: (LocalDate) -> Unit, onEditEntry: (UUID) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val entries = viewModel.entries.collectAsLazyPagingItems()
    val snackbar = remember { SnackbarHostState() }
    HistoryEffects(viewModel.effects, snackbar, viewModel::undoDelete)
    Scaffold(topBar = {
        DhmoTopBar(stringResource(R.string.feature_history_history_title))
    }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        if (entries.itemCount == 0 && entries.loadState.refresh is LoadState.NotLoading) {
            EmptyState(
                stringResource(R.string.feature_history_empty_title),
                stringResource(R.string.feature_history_empty_message),
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (entries.loadState.refresh is LoadState.Loading) {
                    item {
                        val label = stringResource(R.string.feature_history_loading)
                        CircularProgressIndicator(Modifier.padding(24.dp).semantics { contentDescription = label })
                    }
                }
                items(entries.itemCount) { index ->
                    when (val item = entries[index]) {
                        is HistoryItem.DayHeader -> HistoryDayHeader(item, state.units) { onOpenDay(item.date) }

                        is HistoryItem.Entry -> HistoryEntryRow(
                            item.entry,
                            state.units,
                            viewModel.sourceLabel(item.entry.hcDataOrigin),
                            onEdit = { onEditEntry(item.entry.id) },
                            onDelete = { viewModel.deleteEntry(item.entry.id) },
                            onAdopt = { viewModel.adopt(item.entry.id) },
                        )

                        null -> Unit
                    }
                }
            }
        }
    }
}

@Composable
internal fun HistoryEffects(effects: Flow<HistoryEffect>, snackbar: SnackbarHostState, onUndo: (UUID) -> Unit) {
    val deleted = stringResource(R.string.feature_history_deleted)
    val undo = stringResource(R.string.feature_history_undo)
    val error = stringResource(R.string.feature_history_error)
    LaunchedEffect(effects, snackbar) {
        effects.collect { effect ->
            when (effect) {
                is HistoryEffect.Deleted -> if (snackbar.showSnackbar(deleted, undo) == SnackbarResult.ActionPerformed) onUndo(effect.id)
                HistoryEffect.Error -> snackbar.showSnackbar(error)
            }
        }
    }
}

@Composable
internal fun HistoryDayHeader(item: HistoryItem.DayHeader, units: VolumeUnit, onClick: (() -> Unit)? = null) {
    val date = item.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
    val openLabel = stringResource(R.string.feature_history_day_open, date)
    val clickable = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick).semantics { contentDescription = openLabel }
    Column(Modifier.fillMaxWidth().then(clickable).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(date, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VolumeText(item.totalMl, units, style = MaterialTheme.typography.titleSmall)
            val goalText = item.goalMl?.let { VolumeFormatter.format(it, units, Locale.getDefault()) }
            Text(
                if (goalText ==
                    null
                ) {
                    stringResource(R.string.feature_history_goal_unavailable)
                } else {
                    stringResource(R.string.feature_history_goal, goalText)
                },
            )
            val result = dayResult(DayStat(item.date, item.totalMl, item.goalMl, item.entryCount))
            val badge = when (result) {
                DayResult.MET -> casa.peterson.dhmo.core.designsystem.component.DayResult.MET
                DayResult.PARTLY_MET -> casa.peterson.dhmo.core.designsystem.component.DayResult.PARTLY_MET
                DayResult.MISSED -> casa.peterson.dhmo.core.designsystem.component.DayResult.MISSED
                DayResult.NO_DATA -> null
            }
            if (badge != null) DayResultBadge(badge, showLabel = false)
        }
        if (item.anomalous) Text(stringResource(R.string.feature_history_anomaly), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
internal fun HistoryEntryRow(
    entry: IntakeEntry,
    units: VolumeUnit,
    sourceLabel: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAdopt: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                VolumeText(entry.volumeMl, units, style = MaterialTheme.typography.bodyLarge)
                Text(entry.instant.atOffset(entry.zoneOffset).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)))
                if (entry.isImport) {
                    val source = if (sourceLabel ==
                        null
                    ) {
                        stringResource(R.string.feature_history_imported_unknown)
                    } else {
                        stringResource(R.string.feature_history_imported, sourceLabel)
                    }
                    Text(source, style = MaterialTheme.typography.bodySmall)
                }
                entry.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            if (entry.isEditable) {
                IconButton(onClick = onEdit) { Icon(painterResource(DhmoIcons.Edit), stringResource(R.string.feature_history_edit)) }
                IconButton(onClick = onDelete) { Icon(painterResource(DhmoIcons.Delete), stringResource(R.string.feature_history_delete)) }
            }
        }
        if (!entry.isEditable) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.feature_history_read_only), style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = onAdopt) { Text(stringResource(R.string.feature_history_adopt)) }
            }
        }
    }
}
