package casa.peterson.dhmo.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import casa.peterson.dhmo.core.designsystem.component.TimePickerRow
import casa.peterson.dhmo.core.domain.format.VolumeFormatter
import casa.peterson.dhmo.core.model.ActivityLevel
import casa.peterson.dhmo.core.model.Climate
import casa.peterson.dhmo.core.model.ComponentKind
import casa.peterson.dhmo.core.model.GoalBreakdown
import casa.peterson.dhmo.core.model.Sex
import casa.peterson.dhmo.core.model.settings.VolumeUnit

internal enum class OnboardingPage { WELCOME, DISCLAIMER, UNITS, GOAL_MODE, PROFILE, MANUAL, PREVIEW, NOTIFICATION, REMINDERS, DONE }

@Composable
internal fun OnboardingPageContent(
    page: OnboardingPage,
    state: OnboardingState,
    preview: GoalBreakdown?,
    change: ((OnboardingState) -> OnboardingState) -> Unit,
    next: () -> Unit,
    back: () -> Unit,
    allowNotifications: () -> Unit,
    skipNotifications: () -> Unit,
) {
    val title = when (page) {
        OnboardingPage.WELCOME ->
            if (state.returning) R.string.feature_onboarding_welcome_back else R.string.feature_onboarding_welcome_title

        OnboardingPage.DISCLAIMER -> R.string.feature_onboarding_disclaimer_title

        OnboardingPage.UNITS -> R.string.feature_onboarding_units_title

        OnboardingPage.GOAL_MODE -> R.string.feature_onboarding_goal_mode_title

        OnboardingPage.PROFILE -> R.string.feature_onboarding_profile_setup_title

        OnboardingPage.MANUAL -> R.string.feature_onboarding_manual_goal_title

        OnboardingPage.PREVIEW -> R.string.feature_onboarding_goal_preview_title

        OnboardingPage.NOTIFICATION -> R.string.feature_onboarding_notification_rationale_title

        OnboardingPage.REMINDERS -> R.string.feature_onboarding_reminder_setup_title

        OnboardingPage.DONE -> R.string.feature_onboarding_onboarding_done_title
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        when (page) {
            OnboardingPage.WELCOME -> Text(
                stringResource(if (state.returning) R.string.feature_onboarding_back_body else R.string.feature_onboarding_welcome_body),
            )

            OnboardingPage.DISCLAIMER -> {
                Text(stringResource(R.string.feature_onboarding_disclaimer_body))
                Toggle(R.string.feature_onboarding_condition, state.condition) { checked ->
                    change { it.copy(condition = checked, choice = if (checked) GoalChoice.MANUAL else GoalChoice.CALCULATED) }
                }
            }

            OnboardingPage.UNITS -> {
                listOf(
                    VolumeUnit.ML to R.string.feature_onboarding_unit_ml,
                    VolumeUnit.L to R.string.feature_onboarding_unit_l,
                    VolumeUnit.US_FL_OZ to R.string.feature_onboarding_unit_us,
                    VolumeUnit.IMP_FL_OZ to R.string.feature_onboarding_unit_imp,
                ).forEach { (unit, label) -> Choice(label, state.units == unit) { change { it.withUnits(unit) } } }
                MinuteField(R.string.feature_onboarding_day_start, state.dayStart) { value ->
                    change { it.copy(dayStart = value) }
                }
            }

            OnboardingPage.GOAL_MODE -> {
                if (state.condition) Text(stringResource(R.string.feature_onboarding_goal_mode_body))
                Choice(R.string.feature_onboarding_calculated, state.choice == GoalChoice.CALCULATED) {
                    change { it.copy(choice = GoalChoice.CALCULATED) }
                }
                Choice(R.string.feature_onboarding_manual, state.choice == GoalChoice.MANUAL) {
                    change { it.copy(choice = GoalChoice.MANUAL) }
                }
            }

            OnboardingPage.PROFILE -> ProfileFields(state, change)

            OnboardingPage.MANUAL -> NumberField(manualGoalLabel(state.units), state.manualGoalText()) { value ->
                change { it.copy(manualGoal = value) }
            }

            OnboardingPage.PREVIEW -> {
                val locale = LocalConfiguration.current.locales[0]
                val target = if (state.choice == GoalChoice.MANUAL) state.manualGoalMl() else preview?.calculatedMl
                if (target !=
                    null
                ) {
                    Text(stringResource(R.string.feature_onboarding_goal_value, VolumeFormatter.format(target, state.units, locale)), style = MaterialTheme.typography.headlineSmall)
                }
                if (state.choice == GoalChoice.CALCULATED) {
                    preview?.components?.filter { it.applied }?.forEach { component ->
                        Text(
                            stringResource(
                                R.string.feature_onboarding_component_value,
                                stringResource(componentLabel(component.kind)),
                                VolumeFormatter.format(component.ml, state.units, locale),
                            ),
                        )
                    }
                }
                Text(stringResource(R.string.feature_onboarding_goal_disclaimer))
            }

            OnboardingPage.NOTIFICATION -> Text(stringResource(R.string.feature_onboarding_notification_body))

            OnboardingPage.REMINDERS -> {
                Toggle(R.string.feature_onboarding_reminder_enable, state.remindersEnabled) { value ->
                    change { it.copy(remindersEnabled = value) }
                }
                MinuteField(R.string.feature_onboarding_wake, state.wake) { value ->
                    change { it.copy(wake = value) }
                }
                MinuteField(R.string.feature_onboarding_sleep, state.sleep) { value ->
                    change { it.copy(sleep = value) }
                }
            }

            OnboardingPage.DONE -> Text(stringResource(R.string.feature_onboarding_done_body))
        }
        if (state.loading) Text(stringResource(R.string.feature_onboarding_loading))
        if (state.error) Text(stringResource(R.string.feature_onboarding_error), color = MaterialTheme.colorScheme.error)
        if (page == OnboardingPage.NOTIFICATION) {
            Button(onClick = allowNotifications, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.feature_onboarding_allow))
            }
            TextButton(onClick = skipNotifications) { Text(stringResource(R.string.feature_onboarding_not_now)) }
        } else {
            Button(onClick = next, enabled = !state.loading && !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(
                        if (page ==
                            OnboardingPage.DONE
                        ) {
                            R.string.feature_onboarding_finish
                        } else {
                            R.string.feature_onboarding_continue
                        },
                    ),
                )
            }
        }
        if (page != OnboardingPage.WELCOME) TextButton(onClick = back) { Text(stringResource(R.string.feature_onboarding_back)) }
    }
}

@Composable
private fun ProfileFields(state: OnboardingState, change: ((OnboardingState) -> OnboardingState) -> Unit) {
    NumberField(R.string.feature_onboarding_birth_date, state.birthDate, false) { value -> change { it.copy(birthDate = value) } }
    NumberField(R.string.feature_onboarding_weight, state.weight) { value -> change { it.copy(weight = value) } }
    NumberField(R.string.feature_onboarding_height, state.height) { value -> change { it.copy(height = value) } }
    Text(stringResource(R.string.feature_onboarding_sex), style = MaterialTheme.typography.titleMedium)
    listOf(
        Sex.FEMALE to R.string.feature_onboarding_sex_female,
        Sex.MALE to R.string.feature_onboarding_sex_male,
        Sex.UNSPECIFIED to R.string.feature_onboarding_sex_unspecified,
    )
        .forEach { (value, label) -> Choice(label, state.sex == value) { change { it.copy(sex = value) } } }
    Text(stringResource(R.string.feature_onboarding_activity), style = MaterialTheme.typography.titleMedium)
    listOf(
        ActivityLevel.SEDENTARY to R.string.feature_onboarding_sedentary,
        ActivityLevel.LIGHT to R.string.feature_onboarding_light,
        ActivityLevel.MODERATE to R.string.feature_onboarding_moderate,
        ActivityLevel.VERY_ACTIVE to R.string.feature_onboarding_very_active,
    )
        .forEach { (value, label) -> Choice(label, state.activity == value) { change { it.copy(activity = value) } } }
    Text(stringResource(R.string.feature_onboarding_climate), style = MaterialTheme.typography.titleMedium)
    listOf(
        Climate.TEMPERATE to R.string.feature_onboarding_temperate,
        Climate.WARM to R.string.feature_onboarding_warm,
        Climate.HOT_HUMID to R.string.feature_onboarding_hot_humid,
    )
        .forEach { (value, label) -> Choice(label, state.climate == value) { change { it.copy(climate = value) } } }
    Toggle(R.string.feature_onboarding_pregnant, state.pregnant) { value -> change { it.copy(pregnant = value) } }
    Toggle(R.string.feature_onboarding_lactating, state.lactating) { value -> change { it.copy(lactating = value) } }
    Toggle(R.string.feature_onboarding_altitude, state.altitude) { value -> change { it.copy(altitude = value) } }
}

@Composable
private fun Choice(@StringRes label: Int, selected: Boolean, onClick: () -> Unit) {
    Row {
        RadioButton(selected, onClick)
        TextButton(onClick = onClick) { Text(stringResource(label)) }
    }
}

@Composable
private fun Toggle(@StringRes label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Text(stringResource(label), modifier = Modifier.weight(1f))
        Switch(checked, onCheckedChange = null)
    }
}

@Composable
private fun NumberField(@StringRes label: Int, value: String, numeric: Boolean = true, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value,
        onValueChange,
        label = {
            Text(stringResource(label))
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
        ),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun MinuteField(@StringRes label: Int, minutes: Int, onValueChange: (Int) -> Unit) {
    TimePickerRow(stringResource(label), minutes, onValueChange)
}

@StringRes
private fun componentLabel(kind: ComponentKind): Int = when (kind) {
    ComponentKind.BASE_CHILD_TABLE, ComponentKind.BASE_WEIGHT, ComponentKind.BASE_SEX_FLOOR -> R.string.feature_onboarding_component_base
    ComponentKind.PREGNANCY -> R.string.feature_onboarding_component_pregnancy
    ComponentKind.LACTATION -> R.string.feature_onboarding_component_lactation
    ComponentKind.ACTIVITY_LEVEL, ComponentKind.ACTIVITY_MINUTES -> R.string.feature_onboarding_component_activity
    ComponentKind.CLIMATE_MANUAL, ComponentKind.CLIMATE_FORECAST -> R.string.feature_onboarding_component_climate
    ComponentKind.ALTITUDE -> R.string.feature_onboarding_component_altitude
}

@StringRes
private fun manualGoalLabel(unit: VolumeUnit): Int = when (unit) {
    VolumeUnit.ML -> R.string.feature_onboarding_goal_ml
    VolumeUnit.L -> R.string.feature_onboarding_goal_l
    VolumeUnit.US_FL_OZ, VolumeUnit.IMP_FL_OZ -> R.string.feature_onboarding_goal_oz
}
