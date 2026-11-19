package casa.peterson.dhmo.feature.today

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.core.designsystem.component.DeskScene
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.designsystem.component.EmptyState
import casa.peterson.dhmo.core.designsystem.component.NotebookCard
import casa.peterson.dhmo.core.designsystem.component.NotebookRow
import casa.peterson.dhmo.core.designsystem.component.QuickAddButton
import casa.peterson.dhmo.core.designsystem.component.VolumeInputSheet
import casa.peterson.dhmo.core.designsystem.component.WindowHero
import casa.peterson.dhmo.core.designsystem.icon.DhmoIcons
import casa.peterson.dhmo.core.designsystem.icon.drawableRes
import casa.peterson.dhmo.core.designsystem.theme.LocalDhmoExtraColors
import casa.peterson.dhmo.core.designsystem.theme.NumeralFontFamily
import casa.peterson.dhmo.core.domain.format.VolumeFormatter
import casa.peterson.dhmo.core.domain.pace.PaceStatus
import casa.peterson.dhmo.core.model.Container
import casa.peterson.dhmo.core.model.ContainerIcon
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.model.IntakeEntry
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

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
    val undo = stringResource(R.string.feature_today_undo)
    val night = LocalDhmoExtraColors.current.isNight
    val themeAction = stringResource(if (night) R.string.feature_today_switch_day else R.string.feature_today_switch_night)
    LaunchedEffect(viewModel, resources) {
        viewModel.effects.collect { effect ->
            // Each message gets its own coroutine, so a newer one replaces the current toast instead of queueing behind it.
            launch {
                snackbar.currentSnackbarData?.dismiss()
                when (effect) {
                    is TodayEffect.EntryDeleted -> {
                        val message = removedMessage(resources, effect.volumeMl, effect.containerName)
                        val result = snackbar.showSnackbar(message, undo, duration = SnackbarDuration.Long)
                        if (result == SnackbarResult.ActionPerformed) viewModel.onUndoDelete(effect.id)
                    }

                    is TodayEffect.Error -> snackbar.showSnackbar(resources.getString(effect.messageRes))
                }
            }
        }
    }
    Scaffold(topBar = {
        DhmoTopBar(stringResource(R.string.feature_today_today_title), actions = {
            TextButton(
                onClick = { viewModel.setNightMode(!night) },
                enabled = state is TodayUiState.Ready,
                modifier = Modifier.testTag("today_theme_toggle").semantics { contentDescription = themeAction },
            ) {
                Icon(
                    painterResource(if (night) DhmoIcons.Lantern else DhmoIcons.Sun),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(if (night) R.string.feature_today_night else R.string.feature_today_day),
                    Modifier.padding(start = 6.dp),
                )
            }
        })
    }, snackbarHost = { PaperSnackbarHost(snackbar) }) { padding ->
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
                hour = remember { LocalTime.now().hour },
                animated = true,
            )
        }
    }
}

/** "Removed the 250 ml glass." when the drink matches a container, "Removed the 250 ml drink." otherwise. */
private fun removedMessage(resources: android.content.res.Resources, volumeMl: Int?, containerName: String?): String =
    if (volumeMl == null) {
        resources.getString(R.string.feature_today_deleted)
    } else {
        resources.getString(
            R.string.feature_today_deleted_named,
            VolumeFormatter.format(volumeMl, VolumeUnit.ML, Locale.getDefault()),
            containerName?.lowercase() ?: resources.getString(R.string.feature_today_drink),
        )
    }

/** The design's toast: a paper-coloured bar with a hairline border and the action underlined in the primary colour. */
@Composable
private fun PaperSnackbarHost(state: SnackbarHostState) {
    SnackbarHost(state) { data ->
        val shape = MaterialTheme.shapes.medium
        Snackbar(
            modifier = Modifier.padding(horizontal = 12.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
            action = data.visuals.actionLabel?.let { label ->
                {
                    TextButton(onClick = data::performAction) {
                        Text(
                            label,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            textDecoration = TextDecoration.Underline,
                        )
                    }
                }
            },
            shape = shape,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) { Text(data.visuals.message) }
    }
}

private val PortraitHeroWidth = 230.dp
private const val LARGE_FONT_SCALE = 1.3f
private val FlushTextButtonPadding = PaddingValues(vertical = 8.dp)
private const val EVENING_FROM = 17
private const val NIGHT_FROM = 21
private const val AFTERNOON_FROM = 12
private const val MORNING_FROM = 5
private const val NUMERAL_SIZE = 48
private const val GOAL_SIZE = 16

/** Landscape screens are short, so the arch shrinks to leave room for the figure and quick-add below it. */
private val LandscapeHeroWidth = 140.dp

/** The line under the date: the goal, the rain or an empty sill when any of them applies, else the hour's greeting. */
@Composable
private fun greetingFor(state: TodayUiState.Ready, hour: Int): String = when {
    state.goalMl > 0 && state.consumedMl >= state.goalMl -> stringResource(R.string.feature_today_goal_met)
    state.entries.isEmpty() -> stringResource(R.string.feature_today_no_entries)
    state.pace?.status == PaceStatus.BEHIND -> stringResource(R.string.feature_today_greeting_behind)
    hour >= NIGHT_FROM || hour < MORNING_FROM -> stringResource(R.string.feature_today_greeting_night)
    hour >= EVENING_FROM -> stringResource(R.string.feature_today_greeting_evening)
    hour >= AFTERNOON_FROM -> stringResource(R.string.feature_today_greeting_afternoon)
    else -> stringResource(R.string.feature_today_greeting_morning)
}

@Composable
internal fun TodayContent(
    state: TodayUiState.Ready,
    onQuickAdd: (Container) -> Unit,
    onCustomAdd: (Int, java.time.Instant?) -> Unit,
    onDelete: (UUID) -> Unit,
    onEdit: (UUID) -> Unit,
    onOpenGoal: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenHistoryDay: (LocalDate) -> Unit,
    onContainers: () -> Unit,
    onBatteryHelp: () -> Unit,
    modifier: Modifier = Modifier,
    hour: Int = EVENING_FROM + 1,
    animated: Boolean = false,
) {
    var custom by remember { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    val spacing = Arrangement.spacedBy(16.dp)
    val hero: @Composable () -> Unit = { HeroSection(state, hour, animated, landscape, onOpenGoal, onOpenReminders, onBatteryHelp) }
    val coasters: @Composable () -> Unit = { CoasterSection(state, onQuickAdd, onCustom = { custom = true }, onContainers) }
    val notebook: @Composable () -> Unit = {
        NotebookSection(state, onQuickAdd, onCustom = { custom = true }, onDelete, onEdit, onOpenHistoryDay, animated)
    }
    if (landscape) {
        // A short, wide screen: the window and total on the left, what to pour and what was poured on the right.
        Row(modifier.fillMaxSize()) {
            LazyColumn(Modifier.weight(1f), contentPadding = padding, verticalArrangement = spacing) { item { hero() } }
            LazyColumn(Modifier.weight(1f).testTag("today_list"), contentPadding = padding, verticalArrangement = spacing) {
                item { coasters() }
                item { notebook() }
            }
        }
    } else {
        LazyColumn(modifier.fillMaxSize().testTag("today_list"), contentPadding = padding, verticalArrangement = spacing) {
            item { hero() }
            item { coasters() }
            item { notebook() }
        }
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

/** The date and greeting beside the desk, the window, the total and the pace line. */
@Composable
private fun HeroSection(
    state: TodayUiState.Ready,
    hour: Int,
    animated: Boolean,
    landscape: Boolean,
    onOpenGoal: () -> Unit,
    onOpenReminders: () -> Unit,
    onBatteryHelp: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    state.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    greetingFor(state, hour),
                    modifier = Modifier.heightIn(min = 64.dp * LocalDensity.current.fontScale),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            DeskScene(animated = animated)
        }
        WindowHero(
            state.progress,
            width = if (landscape) LandscapeHeroWidth else PortraitHeroWidth,
            raining = state.pace?.status == PaceStatus.BEHIND,
            animated = animated,
            sipEvent = state.sipEvent,
            goalEvent = state.goalEvent,
        )
        TotalLine(state, locale, onOpenGoal)
        if (state.goalMode != GoalMode.CALCULATED) Text(stringResource(R.string.feature_today_goal_override))
        state.pace?.let { pace ->
            TextButton(onClick = onOpenReminders) {
                Text(
                    if (pace.status == PaceStatus.BEHIND) {
                        stringResource(R.string.feature_today_behind_amount, VolumeFormatter.format(pace.deficitMl, state.units, locale))
                    } else {
                        stringResource(
                            if (pace.status ==
                                PaceStatus.AHEAD
                            ) {
                                R.string.feature_today_ahead
                            } else {
                                R.string.feature_today_on_track
                            },
                        )
                    },
                )
            }
        }
        if (state.showDisclaimerNote) Text(stringResource(R.string.feature_today_disclaimer))
        if (state.showBatteryHint) TextButton(onClick = onBatteryHelp) { Text(stringResource(R.string.feature_today_battery_hint)) }
    }
}

/** The coasters for the user's containers: equal widths, three to a row (two at large font sizes), wrapping. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoasterSection(state: TodayUiState.Ready, onQuickAdd: (Container) -> Unit, onCustom: () -> Unit, onContainers: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = if (LocalConfiguration.current.fontScale > LARGE_FONT_SCALE) 2 else 3,
        ) {
            state.containers.forEach { container ->
                QuickAddButton(
                    container.volumeMl,
                    state.units,
                    onClick = { onQuickAdd(container) },
                    modifier = Modifier.weight(1f).testTag("quick_add_${container.id}"),
                    label = container.name,
                    icon = container.icon.drawableRes(),
                )
            }
        }
        // No side padding, so the text lines up with the coasters above.
        Column {
            TextButton(onClick = onCustom, contentPadding = FlushTextButtonPadding) {
                Text(stringResource(R.string.feature_today_add_custom))
            }
            TextButton(onClick = onContainers, contentPadding = FlushTextButtonPadding) {
                Text(stringResource(R.string.feature_today_manage_containers))
            }
        }
    }
}

/** "Poured today": the day's drinks as notebook rows, or the sleeping cat and a "Pour a glass" button when there are none. */
@Composable
private fun NotebookSection(
    state: TodayUiState.Ready,
    onQuickAdd: (Container) -> Unit,
    onCustom: () -> Unit,
    onDelete: (UUID) -> Unit,
    onEdit: (UUID) -> Unit,
    onOpenHistoryDay: (LocalDate) -> Unit,
    animated: Boolean,
) {
    NotebookCard(
        title = stringResource(R.string.feature_today_recent_drinks),
        count = pluralStringResource(R.plurals.feature_today_drink_count, state.entries.size, state.entries.size),
    ) {
        if (state.entries.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.feature_today_empty_title),
                message = stringResource(R.string.feature_today_empty_message),
                actionLabel = stringResource(R.string.feature_today_pour_a_glass),
                actionIcon = DhmoIcons.Glass,
                onAction = { state.containers.firstOrNull()?.let(onQuickAdd) ?: onCustom() },
                animated = animated,
            )
        } else {
            state.entries.forEachIndexed { index, entry ->
                TodayEntry(entry, state.units, state.containers, onDelete, onEdit, last = index == state.entries.lastIndex)
            }
            TextButton(onClick = { onOpenHistoryDay(state.day) }, contentPadding = FlushTextButtonPadding) {
                Text(stringResource(R.string.feature_today_history))
            }
        }
    }
}

/** "1,250 / 2,000 ml": the day's total in big monospaced figures, the goal beside it opening the goal editor. */
@Composable
private fun TotalLine(state: TodayUiState.Ready, locale: Locale, onOpenGoal: () -> Unit) {
    val total = VolumeFormatter.formatNumber(state.consumedMl, state.units, locale)
    val goal = VolumeFormatter.format(state.goalMl, state.units, locale)
    val goalLabel = stringResource(R.string.feature_today_open_goal)
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontSize = NUMERAL_SIZE.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.03).em)) { append(total) }
            withStyle(SpanStyle(fontSize = GOAL_SIZE.sp, color = Color.Unspecified)) { append(" / $goal") }
        },
        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = NumeralFontFamily),
        modifier = Modifier
            .testTag("today_goal")
            .clickable(onClickLabel = goalLabel, role = Role.Button, onClick = onOpenGoal)
            .semantics(mergeDescendants = true) {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayEntry(
    entry: IntakeEntry,
    unit: VolumeUnit,
    containers: List<Container>,
    onDelete: (UUID) -> Unit,
    onEdit: (UUID) -> Unit,
    last: Boolean,
) {
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
    val container = containers.firstOrNull { it.volumeMl == entry.volumeMl }
    SwipeToDismissBox(
        modifier = Modifier.testTag("today_entry_${entry.id}").semantics { customActions = actions },
        state = dismiss,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = entry.isEditable,
        backgroundContent = {
            Text(stringResource(R.string.feature_today_delete), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        },
    ) {
        NotebookRow(
            time = entry.instant.atOffset(entry.zoneOffset).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)),
            amount = VolumeFormatter.format(entry.volumeMl, unit, locale),
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(enabled = entry.isEditable, onClickLabel = editLabel, role = Role.Button) { onEdit(entry.id) },
            icon = (container?.icon ?: ContainerIcon.GLASS).drawableRes(),
            label = container?.name,
            last = last,
            trailing = if (entry.isEditable) {
                {
                    IconButton(onClick = { onDelete(entry.id) }) {
                        Icon(painterResource(DhmoIcons.Close), contentDescription = deleteLabel, modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                null
            },
        )
    }
}
