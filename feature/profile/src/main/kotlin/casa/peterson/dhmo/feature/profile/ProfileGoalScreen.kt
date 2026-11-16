package casa.peterson.dhmo.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.core.designsystem.component.DhmoScreen
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.designsystem.component.SectionCard
import casa.peterson.dhmo.core.designsystem.component.SettingsRow
import casa.peterson.dhmo.core.designsystem.icon.DhmoIcons
import casa.peterson.dhmo.core.model.ActivityAdjustment
import casa.peterson.dhmo.core.model.ClampFlag
import casa.peterson.dhmo.core.model.GoalBreakdown
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.model.Intensity
import java.text.NumberFormat
import java.time.LocalDate

@Composable
internal fun ProfileGoalScreen(
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onGlobalOverride: () -> Unit,
    onDayOverride: (LocalDate) -> Unit,
    onAdjustments: () -> Unit,
    onSources: () -> Unit,
    viewModel: ProfileGoalViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DhmoScreen(topBar = { DhmoTopBar(stringResource(R.string.feature_profile_profile_goal_title), onNavigateBack = onBack) }) { padding ->
        when (val current = state) {
            ProfileGoalUiState.Loading -> Text(
                stringResource(R.string.feature_profile_loading),
                modifier = Modifier.padding(padding).padding(16.dp),
            )

            is ProfileGoalUiState.Ready -> ProfileGoalContent(
                current,
                ProfileGoalActions(
                    onEditProfile = onEditProfile,
                    onGlobalOverride = onGlobalOverride,
                    onDayOverride = { onDayOverride(current.day) },
                    onClearDayOverride = viewModel::onClearTodayOverride,
                    onAdjustments = onAdjustments,
                    onSources = onSources,
                    onAddPlanned = viewModel::onAddPlanned,
                    onRemovePlanned = viewModel::onRemovePlanned,
                ),
                contentPadding = padding,
            )
        }
    }
}

internal class ProfileGoalActions(
    val onEditProfile: () -> Unit = {},
    val onGlobalOverride: () -> Unit = {},
    val onDayOverride: () -> Unit = {},
    val onClearDayOverride: () -> Unit = {},
    val onAdjustments: () -> Unit = {},
    val onSources: () -> Unit = {},
    /** Returns false when the minutes are out of range. */
    val onAddPlanned: (Int, Intensity) -> Boolean = { _, _ -> true },
    val onRemovePlanned: (String) -> Unit = {},
)

@Composable
internal fun ProfileGoalContent(
    state: ProfileGoalUiState.Ready,
    actions: ProfileGoalActions,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val labels = breakdownLabels(state.units)
    val direction = LocalLayoutDirection.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("profile_goal_list"),
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(direction) + 16.dp,
            end = contentPadding.calculateEndPadding(direction) + 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "summary") { GoalSummary(state, labels) }
        item(key = "today") { TodayOverrideCard(state, labels, actions) }
        state.breakdown?.let { breakdown -> item(key = "breakdown") { BreakdownCard(breakdown, labels) } }
        if (state.profile != null) item(key = "planned") { PlannedExerciseCard(state, actions) }
        item(key = "rows") { GoalRows(state, labels, actions) }
        item(key = "disclaimer") {
            Text(stringResource(R.string.feature_profile_disclaimer), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GoalSummary(state: ProfileGoalUiState.Ready, labels: BreakdownLabels) {
    SectionCard {
        Text(stringResource(R.string.feature_profile_effective_goal), style = MaterialTheme.typography.titleMedium)
        Text(labels.volume(state.effective.targetMl), style = MaterialTheme.typography.displaySmall)
        Text(
            stringResource(
                when (state.effective.mode) {
                    GoalMode.CALCULATED -> R.string.feature_profile_mode_calculated
                    GoalMode.GLOBAL_OVERRIDE -> R.string.feature_profile_mode_global
                    GoalMode.DAY_OVERRIDE -> R.string.feature_profile_mode_day
                },
            ),
        )
        val calculated = state.effective.calculatedMl
        Text(
            if (calculated != null) {
                stringResource(R.string.feature_profile_calculated_goal, labels.volume(calculated))
            } else {
                stringResource(R.string.feature_profile_calculated_goal_none)
            },
            style = MaterialTheme.typography.titleSmall,
        )
        val breakdown = state.breakdown
        if (breakdown != null && breakdown.clamp != ClampFlag.NONE) {
            ErrorText(
                stringResource(
                    if (breakdown.clamp == ClampFlag.HIGH) R.string.feature_profile_clamp_high else R.string.feature_profile_clamp_low,
                    labels.volume(breakdown.rawMl),
                    labels.volume(breakdown.clampedMl),
                ),
            )
        }
    }
}

@Composable
private fun TodayOverrideCard(state: ProfileGoalUiState.Ready, labels: BreakdownLabels, actions: ProfileGoalActions) {
    SectionCard(title = stringResource(R.string.feature_profile_today_override_title)) {
        val override = state.todayOverride
        if (override == null) {
            Text(stringResource(R.string.feature_profile_today_override_none))
            OutlinedButton(onClick = actions.onDayOverride) { Text(stringResource(R.string.feature_profile_set_today_goal)) }
        } else {
            Text(stringResource(R.string.feature_profile_today_override_active, labels.volume(override.targetMl)))
            override.reason?.let { Text(stringResource(R.string.feature_profile_today_override_reason, it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = actions.onDayOverride) { Text(stringResource(R.string.feature_profile_change)) }
                TextButton(onClick = actions.onClearDayOverride) { Text(stringResource(R.string.feature_profile_clear)) }
            }
        }
    }
}

@Composable
private fun BreakdownCard(breakdown: GoalBreakdown, labels: BreakdownLabels) {
    SectionCard(title = stringResource(R.string.feature_profile_breakdown_title)) {
        breakdown.components.forEach { component ->
            Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                Text(
                    labels.label(component),
                    textDecoration = if (component.applied) null else TextDecoration.LineThrough,
                    color = if (component.applied) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!component.applied) {
                    Text(
                        stringResource(R.string.feature_profile_breakdown_not_applied),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                labels.note(component)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider()
        Text(
            stringResource(R.string.feature_profile_breakdown_total, labels.volume(breakdown.rawMl), labels.volume(breakdown.calculatedMl)),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(stringResource(R.string.feature_profile_drinks_only), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PlannedExerciseCard(state: ProfileGoalUiState.Ready, actions: ProfileGoalActions) {
    SectionCard(title = stringResource(R.string.feature_profile_planned_title)) {
        if (state.pinned.pinActivityLevel) Text(stringResource(R.string.feature_profile_planned_pinned))
        if (state.planned.isEmpty()) {
            Text(stringResource(R.string.feature_profile_planned_none))
        } else {
            state.planned.forEach { session -> PlannedRow(session, actions.onRemovePlanned) }
        }
        var minutes by rememberSaveable { mutableStateOf("") }
        var intensity by rememberSaveable { mutableStateOf(Intensity.MODERATE) }
        var invalid by rememberSaveable { mutableStateOf(false) }
        FormTextField(
            label = stringResource(R.string.feature_profile_planned_minutes),
            value = minutes,
            onValueChange = {
                minutes = it
                invalid = false
            },
            keyboardType = KeyboardType.Number,
            isError = invalid,
        )
        RadioGroup(
            title = stringResource(R.string.feature_profile_intensity),
            options = Intensity.entries.map { it to intensityLabel(it) },
            selected = intensity,
            onSelect = { intensity = it },
        )
        if (invalid) ErrorText(stringResource(R.string.feature_profile_planned_invalid))
        Button(onClick = {
            val value = minutes.trim().toIntOrNull()
            invalid = value == null || !actions.onAddPlanned(value, intensity)
            if (!invalid) minutes = ""
        }) { Text(stringResource(R.string.feature_profile_planned_add)) }
    }
}

@Composable
private fun PlannedRow(session: ActivityAdjustment, onRemove: (String) -> Unit) {
    val label = stringResource(R.string.feature_profile_planned_session, session.minutes, intensityLabel(session.intensity))
    val remove = stringResource(R.string.feature_profile_planned_remove, label)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        IconButton(onClick = { onRemove(session.sourceRecordId) }, modifier = Modifier.semantics { contentDescription = remove }) {
            Icon(painterResource(DhmoIcons.Delete), contentDescription = null)
        }
    }
}

@Composable
internal fun intensityLabel(intensity: Intensity): String = stringResource(
    when (intensity) {
        Intensity.LIGHT -> R.string.feature_profile_intensity_light
        Intensity.MODERATE -> R.string.feature_profile_intensity_moderate
        Intensity.VIGOROUS -> R.string.feature_profile_intensity_vigorous
    },
)

@Composable
private fun GoalRows(state: ProfileGoalUiState.Ready, labels: BreakdownLabels, actions: ProfileGoalActions) {
    SectionCard(title = stringResource(R.string.feature_profile_more_title)) {
        val profile = state.profile
        val age = state.breakdown?.ageYears
        SettingsRow(
            title = stringResource(R.string.feature_profile_row_profile),
            summary = if (profile == null || age == null) {
                stringResource(R.string.feature_profile_row_profile_missing)
            } else {
                stringResource(R.string.feature_profile_row_profile_summary, age, weightText(profile.weightKg))
            },
            leadingIcon = DhmoIcons.Edit,
            onClick = actions.onEditProfile,
        )
        SettingsRow(
            title = stringResource(R.string.feature_profile_global_override_title),
            summary = if (state.globalOverride.enabled) {
                stringResource(R.string.feature_profile_row_fixed_on, labels.volume(state.globalOverride.targetMl))
            } else {
                stringResource(R.string.feature_profile_row_fixed_off)
            },
            onClick = actions.onGlobalOverride,
        )
        SettingsRow(
            title = stringResource(R.string.feature_profile_adjustments_title),
            summary = stringResource(R.string.feature_profile_row_adjustments_summary),
            onClick = actions.onAdjustments,
        )
        SettingsRow(
            title = stringResource(R.string.feature_profile_sources_title),
            summary = stringResource(R.string.feature_profile_row_sources_summary),
            onClick = actions.onSources,
        )
    }
}

@Composable
private fun weightText(kg: Double): String {
    val format = NumberFormat.getNumberInstance(LocalConfiguration.current.locales[0])
    format.maximumFractionDigits = 1
    return format.format(kg)
}
