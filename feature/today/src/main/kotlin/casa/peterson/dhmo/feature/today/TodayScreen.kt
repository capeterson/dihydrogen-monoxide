package casa.peterson.dhmo.feature.today

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.designsystem.component.GoalChip
import casa.peterson.dhmo.core.designsystem.component.QuickAddButton
import casa.peterson.dhmo.core.designsystem.component.VolumeInputSheet
import casa.peterson.dhmo.core.designsystem.component.VolumeText
import casa.peterson.dhmo.core.designsystem.component.WindowHero
import casa.peterson.dhmo.core.designsystem.theme.NumeralHeroStyle
import casa.peterson.dhmo.core.domain.format.VolumeFormatter
import casa.peterson.dhmo.core.domain.pace.PaceStatus
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.model.IntakeEntry
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

@Composable
internal fun TodayScreen(
    onOpenGoal: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenHistoryDay: (LocalDate) -> Unit,
    onEdit: (UUID) -> Unit,
    onContainers: () -> Unit,
    onBatteryHelp: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val deleted = stringResource(R.string.feature_today_deleted)
    val undo = stringResource(R.string.feature_today_undo)
    LaunchedEffect(viewModel, resources) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TodayEffect.EntryDeleted -> if (snackbar.showSnackbar(deleted, undo) == SnackbarResult.ActionPerformed) {
                    viewModel.onUndoDelete(effect.id)
                }

                is TodayEffect.Error -> snackbar.showSnackbar(resources.getString(effect.messageRes))
            }
        }
    }
    Scaffold(topBar = {
        DhmoTopBar(stringResource(R.string.feature_today_today_title))
    }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        when (val current = state) {
            TodayUiState.Loading -> Unit

            is TodayUiState.Ready -> TodayContent(
                current,
                onQuickAdd = viewModel::onQuickAdd,
                onCustomAdd = viewModel::onCustomAdd,
                onDelete = viewModel::onDelete,
                onEdit = onEdit,
                onOpenGoal = onOpenGoal,
                onOpenReminders = onOpenReminders,
                onOpenHistoryDay = onOpenHistoryDay,
                onContainers = onContainers,
                onBatteryHelp = onBatteryHelp,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

private val PortraitHeroWidth = 240.dp

/** Landscape screens are short, so the arch shrinks to leave room for the figure and quick-add below it. */
private val LandscapeHeroWidth = 140.dp

@Composable
internal fun TodayContent(
    state: TodayUiState.Ready,
    onQuickAdd: (casa.peterson.dhmo.core.model.Container) -> Unit,
    onCustomAdd: (Int, java.time.Instant?) -> Unit,
    onDelete: (UUID) -> Unit,
    onEdit: (UUID) -> Unit,
    onOpenGoal: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenHistoryDay: (LocalDate) -> Unit,
    onContainers: () -> Unit,
    onBatteryHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var custom by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    LazyColumn(modifier.fillMaxSize().testTag("today_list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    state.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)),
                    style = MaterialTheme.typography.titleMedium,
                )
                WindowHero(
                    state.progress,
                    modifier = Modifier.padding(top = 12.dp),
                    width = if (landscape) LandscapeHeroWidth else PortraitHeroWidth,
                    raining = state.pace?.status == PaceStatus.BEHIND,
                ) {
                    VolumeText(state.consumedMl, state.units, style = NumeralHeroStyle)
                }
                GoalChip(state.goalMl, state.units, onClick = onOpenGoal)
                if (state.goalMl > 0 && state.consumedMl >= state.goalMl) {
                    Text(
                        stringResource(R.string.feature_today_goal_met),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (state.goalMode != GoalMode.CALCULATED) Text(stringResource(R.string.feature_today_goal_override))
                state.pace?.let { pace ->
                    val label = when (pace.status) {
                        PaceStatus.AHEAD -> R.string.feature_today_ahead
                        PaceStatus.ON_TRACK -> R.string.feature_today_on_track
                        PaceStatus.BEHIND -> R.string.feature_today_behind
                    }
                    TextButton(onClick = onOpenReminders) {
                        if (pace.status == PaceStatus.BEHIND) {
                            Text(
                                stringResource(
                                    R.string.feature_today_behind_amount,
                                    VolumeFormatter.format(pace.deficitMl, state.units, locale),
                                ),
                            )
                        } else {
                            Text(stringResource(label))
                        }
                    }
                }
                if (state.showDisclaimerNote) Text(stringResource(R.string.feature_today_disclaimer))
                if (state.showBatteryHint) TextButton(onClick = onBatteryHelp) { Text(stringResource(R.string.feature_today_battery_hint)) }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.feature_today_quick_add), style = MaterialTheme.typography.titleMedium)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.containers.forEach { container ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(container.name)
                                QuickAddButton(container.volumeMl, state.units, onClick = {
                                    onQuickAdd(container)
                                }, modifier = Modifier.testTag("quick_add_${container.id}"))
                            }
                        }
                    }
                    Column {
                        TextButton(onClick = { custom = true }) { Text(stringResource(R.string.feature_today_add_custom)) }
                        TextButton(onClick = onContainers) { Text(stringResource(R.string.feature_today_manage_containers)) }
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.feature_today_recent_drinks), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { onOpenHistoryDay(state.day) }) { Text(stringResource(R.string.feature_today_history)) }
            }
        }
        if (state.entries.isEmpty()) item { Text(stringResource(R.string.feature_today_no_entries), Modifier.padding(16.dp)) }
        items(state.entries, key = { it.id }) { entry -> TodayEntry(entry, state.units, onDelete, onEdit) }
    }
    if (custom) {
        VolumeInputSheet(
            title = stringResource(R.string.feature_today_add_custom),
            initialMl = 250,
            unit = state.units,
            onConfirm = { amount ->
                custom = false
                onCustomAdd(amount, null)
            },
            onDismiss = { custom = false },
            minMl = 1,
            maxMl = 10_000,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayEntry(entry: IntakeEntry, unit: VolumeUnit, onDelete: (UUID) -> Unit, onEdit: (UUID) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val dismiss = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismiss, entry.id, entry.isEditable) {
        snapshotFlow { dismiss.currentValue }.collect { value ->
            if (value == SwipeToDismissBoxValue.EndToStart && entry.isEditable) {
                onDelete(entry.id)
                dismiss.snapTo(SwipeToDismissBoxValue.Settled)
            }
        }
    }
    // Swiping is not discoverable with TalkBack or switch access, so the same actions are offered as custom actions.
    val editLabel = stringResource(R.string.feature_today_edit)
    val deleteLabel = stringResource(R.string.feature_today_delete)
    val actions = if (entry.isEditable) {
        listOf(
            CustomAccessibilityAction(editLabel) { onEdit(entry.id).let { true } },
            CustomAccessibilityAction(deleteLabel) { onDelete(entry.id).let { true } },
        )
    } else {
        emptyList()
    }
    SwipeToDismissBox(
        modifier = Modifier.testTag("today_entry_${entry.id}").semantics { customActions = actions },
        state = dismiss,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = entry.isEditable,
        backgroundContent = { Text(stringResource(R.string.feature_today_delete), Modifier.padding(16.dp)) },
    ) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(
                Modifier.fillMaxWidth()
                    .clickable(enabled = entry.isEditable, onClickLabel = editLabel, role = Role.Button) { onEdit(entry.id) }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                VolumeText(entry.volumeMl, unit)
                Text(
                    entry.instant.atOffset(
                        entry.zoneOffset,
                    ).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)),
                )
            }
        }
    }
}
