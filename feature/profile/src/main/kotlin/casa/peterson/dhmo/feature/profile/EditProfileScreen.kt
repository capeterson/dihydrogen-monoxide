package casa.peterson.dhmo.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.core.designsystem.component.BirthDateField
import casa.peterson.dhmo.core.designsystem.component.BodyMeasurementFields
import casa.peterson.dhmo.core.designsystem.component.DhmoScreen
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.designsystem.component.SwitchRow
import casa.peterson.dhmo.core.model.ActivityLevel
import casa.peterson.dhmo.core.model.Climate
import casa.peterson.dhmo.core.model.Gender

@Composable
internal fun EditProfileScreen(onBack: () -> Unit, viewModel: EditProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.saved.collect { onBack() } }
    DhmoScreen(topBar = { DhmoTopBar(stringResource(R.string.feature_profile_edit_profile_title), onNavigateBack = onBack) }) { padding ->
        EditProfileContent(
            state,
            onChange = viewModel::change,
            onSave = viewModel::onSave,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        )
    }
}

@Composable
internal fun EditProfileContent(
    state: EditProfileUiState,
    onChange: ((EditProfileUiState) -> EditProfileUiState) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.loading) {
            Text(stringResource(R.string.feature_profile_loading))
            return@Column
        }
        BirthDateField(state.birthDate, { value -> onChange { it.copy(birthDate = value) } }, isError = state.invalid)
        BodyMeasurementFields(state.body, { body -> onChange { it.copy(body = body) } }, isError = state.invalid)
        RadioGroup(
            stringResource(R.string.feature_profile_gender),
            listOf(
                Gender.FEMALE to stringResource(R.string.feature_profile_gender_female),
                Gender.MALE to stringResource(R.string.feature_profile_gender_male),
                Gender.NON_BINARY to stringResource(R.string.feature_profile_gender_non_binary),
                Gender.UNSPECIFIED to stringResource(R.string.feature_profile_gender_unspecified),
            ),
            state.gender,
        ) { value -> onChange { it.copy(gender = value) } }
        RadioGroup(
            stringResource(R.string.feature_profile_activity),
            listOf(
                ActivityLevel.SEDENTARY to stringResource(R.string.feature_profile_level_sedentary),
                ActivityLevel.LIGHT to stringResource(R.string.feature_profile_level_light),
                ActivityLevel.MODERATE to stringResource(R.string.feature_profile_level_moderate),
                ActivityLevel.VERY_ACTIVE to stringResource(R.string.feature_profile_level_very_active),
            ),
            state.activity,
        ) { value -> onChange { it.copy(activity = value) } }
        RadioGroup(
            stringResource(R.string.feature_profile_climate),
            listOf(
                Climate.TEMPERATE to stringResource(R.string.feature_profile_climate_temperate),
                Climate.WARM to stringResource(R.string.feature_profile_climate_warm),
                Climate.HOT_HUMID to stringResource(R.string.feature_profile_climate_hot_humid),
            ),
            state.climate,
        ) { value -> onChange { it.copy(climate = value) } }
        SwitchRow(stringResource(R.string.feature_profile_pregnant), state.pregnant, { value -> onChange { it.copy(pregnant = value) } })
        SwitchRow(stringResource(R.string.feature_profile_lactating), state.lactating, { value -> onChange { it.copy(lactating = value) } })
        SwitchRow(
            stringResource(R.string.feature_profile_altitude),
            state.highAltitude,
            { value -> onChange { it.copy(highAltitude = value) } },
        )
        if (state.invalid) ErrorText(stringResource(R.string.feature_profile_profile_invalid))
        Button(onClick = onSave, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.feature_profile_save))
        }
    }
}
