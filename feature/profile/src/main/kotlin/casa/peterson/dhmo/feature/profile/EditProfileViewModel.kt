package casa.peterson.dhmo.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.data.repository.ProfileRepository
import casa.peterson.dhmo.core.data.time.DayTicker
import casa.peterson.dhmo.core.data.usecase.goal.SaveProfileUseCase
import casa.peterson.dhmo.core.model.ActivityLevel
import casa.peterson.dhmo.core.model.Climate
import casa.peterson.dhmo.core.model.PregnancyStatus
import casa.peterson.dhmo.core.model.Profile
import casa.peterson.dhmo.core.model.Sex
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val WEIGHT_RANGE_KG = 2.0..400.0
private val HEIGHT_RANGE_CM = 30.0..272.0
private const val MAX_AGE_YEARS = 130L

internal data class EditProfileUiState(
    val loading: Boolean = true,
    val birthDate: String = "",
    val weight: String = "",
    val height: String = "",
    val sex: Sex = Sex.UNSPECIFIED,
    val activity: ActivityLevel = ActivityLevel.SEDENTARY,
    val climate: Climate = Climate.TEMPERATE,
    val pregnant: Boolean = false,
    val lactating: Boolean = false,
    val highAltitude: Boolean = false,
    val invalid: Boolean = false,
    val saving: Boolean = false,
)

/** Edits the profile. Saving writes a new snapshot effective today (TECHNICAL_PLAN §4.3) and re-snapshots today's goal. */
@HiltViewModel
internal class EditProfileViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val ticker: DayTicker,
    private val saveProfile: SaveProfileUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(EditProfileUiState())
    val state: StateFlow<EditProfileUiState> = mutableState.asStateFlow()
    private val savedChannel = Channel<Unit>(Channel.BUFFERED)

    /** Emits once the profile has been saved; the screen then closes. */
    val saved: Flow<Unit> = savedChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val profile = profiles.observeCurrent().first()
            mutableState.value = if (profile == null) {
                EditProfileUiState(loading = false)
            } else {
                EditProfileUiState(
                    loading = false,
                    birthDate = profile.birthDate.toString(),
                    weight = plain(profile.weightKg),
                    height = profile.heightCm?.let(::plain).orEmpty(),
                    sex = profile.sex,
                    activity = profile.activityLevel,
                    climate = profile.climate,
                    pregnant = profile.pregnancyStatus == PregnancyStatus.PREGNANT,
                    lactating = profile.lactating,
                    highAltitude = profile.highAltitude,
                )
            }
        }
    }

    fun change(transform: (EditProfileUiState) -> EditProfileUiState) = mutableState.update { transform(it).copy(invalid = false) }

    fun onSave() {
        val state = mutableState.value
        if (state.loading || state.saving) return
        viewModelScope.launch {
            val profile = parse(state, ticker.today().first())
            if (profile == null) {
                mutableState.update { it.copy(invalid = true) }
                return@launch
            }
            mutableState.update { it.copy(saving = true) }
            saveProfile(profile)
            savedChannel.send(Unit)
        }
    }

    private fun parse(state: EditProfileUiState, today: LocalDate): Profile? {
        val birthDate = try {
            LocalDate.parse(state.birthDate.trim())
        } catch (_: DateTimeParseException) {
            null
        }?.takeIf { !it.isAfter(today) && !it.isBefore(today.minusYears(MAX_AGE_YEARS)) }
        val weight = number(state.weight)?.takeIf { it in WEIGHT_RANGE_KG }
        val height = number(state.height)?.takeIf { it in HEIGHT_RANGE_CM }
        val heightValid = state.height.isBlank() || height != null
        if (birthDate == null || weight == null || !heightValid) return null
        return Profile(
            birthDate = birthDate,
            sex = state.sex,
            weightKg = weight,
            heightCm = height,
            activityLevel = state.activity,
            climate = state.climate,
            pregnancyStatus = if (state.pregnant) PregnancyStatus.PREGNANT else PregnancyStatus.NONE,
            lactating = state.lactating,
            highAltitude = state.highAltitude,
        )
    }

    private fun number(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun plain(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}
