package casa.peterson.dhmo.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.common.ZoneProvider
import casa.peterson.dhmo.core.data.repository.ContainerRepository
import casa.peterson.dhmo.core.data.repository.DeviceStateRepository
import casa.peterson.dhmo.core.data.repository.ProfileRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.usecase.onboarding.CompleteOnboardingUseCase
import casa.peterson.dhmo.core.domain.body.BodyInputs
import casa.peterson.dhmo.core.domain.goal.GoalCalculator
import casa.peterson.dhmo.core.domain.time.hydrationDay
import casa.peterson.dhmo.core.model.ActivityInput
import casa.peterson.dhmo.core.model.ActivityLevel
import casa.peterson.dhmo.core.model.Climate
import casa.peterson.dhmo.core.model.ClimateInput
import casa.peterson.dhmo.core.model.Gender
import casa.peterson.dhmo.core.model.GoalBreakdown
import casa.peterson.dhmo.core.model.GoalInput
import casa.peterson.dhmo.core.model.PregnancyStatus
import casa.peterson.dhmo.core.model.Profile
import casa.peterson.dhmo.core.model.settings.BodyUnits
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal enum class GoalChoice { CALCULATED, MANUAL }

internal data class OnboardingState(
    val loading: Boolean = true,
    val returning: Boolean = false,
    val resumeStep: String? = null,
    val units: VolumeUnit = VolumeUnit.ML,
    val dayStart: Int = 0,
    val condition: Boolean = false,
    val choice: GoalChoice = GoalChoice.CALCULATED,
    /** ISO date, `1990-05-10`, or empty. It is only shown through [casa.peterson.dhmo.core.designsystem.component.BirthDateField]. */
    val birthDate: String = "",
    val body: BodyInputs = BodyInputs(),
    val gender: Gender = Gender.UNSPECIFIED,
    val activity: ActivityLevel = ActivityLevel.SEDENTARY,
    val climate: Climate = Climate.TEMPERATE,
    val pregnant: Boolean = false,
    val lactating: Boolean = false,
    val altitude: Boolean = false,
    val manualGoal: String = "2000",
    val notificationsGranted: Boolean = false,
    val remindersEnabled: Boolean = false,
    val wake: Int = 420,
    val sleep: Int = 1380,
    val error: Boolean = false,
    val busy: Boolean = false,
)

@HiltViewModel
internal class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val device: DeviceStateRepository,
    private val profiles: ProfileRepository,
    private val containers: ContainerRepository,
    private val complete: CompleteOnboardingUseCase,
    private val calculator: GoalCalculator,
    private val clock: Clock,
    private val zone: ZoneProvider,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(OnboardingState())
    private var resumeHandled = false
    val state: StateFlow<OnboardingState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val current = settings.current()
            val onboarding = device.current().onboarding
            val profile = profiles.observeCurrent().first()
            val isReturning = profile != null || containers.getAll().isNotEmpty()
            mutableState.value = OnboardingState(
                loading = false,
                returning = isReturning,
                resumeStep = onboarding.lastStep,
                units = saved.get<String>("units")?.let(VolumeUnit::valueOf) ?: current.units,
                dayStart = saved["dayStart"] ?: current.dayStartMinutes,
                condition = saved["condition"] ?: onboarding.manualModeRecommended,
                choice = GoalChoice.valueOf(saved["choice"] ?: if (onboarding.manualModeRecommended) "MANUAL" else "CALCULATED"),
                birthDate = saved["birthDate"] ?: "",
                body = BodyInputs(
                    units = saved.get<String>("bodyUnits")?.let(BodyUnits::valueOf) ?: firstRunBodyUnits(isReturning, current.bodyUnits),
                    weight = saved["weight"] ?: "",
                    heightCm = saved["height"] ?: "",
                    heightFeet = saved["heightFeet"] ?: "",
                    heightInches = saved["heightInches"] ?: "",
                ),
                gender = Gender.valueOf(saved["gender"] ?: "UNSPECIFIED"),
                activity = ActivityLevel.valueOf(saved["activity"] ?: "SEDENTARY"),
                climate = Climate.valueOf(saved["climate"] ?: "TEMPERATE"),
                pregnant = saved["pregnant"] ?: false,
                lactating = saved["lactating"] ?: false,
                altitude = saved["altitude"] ?: false,
                manualGoal = saved["manualGoal"] ?: "2000",
                wake = saved["wake"] ?: current.reminders.wakeMinutes,
                sleep = saved["sleep"] ?: current.reminders.sleepMinutes,
                remindersEnabled = saved["remindersEnabled"] ?: current.reminders.enabled,
                notificationsGranted = saved["notificationsGranted"] ?: false,
            )
        }
    }

    fun takeResumeStep(): String? {
        if (resumeHandled) return null
        resumeHandled = true
        return state.value.resumeStep
    }

    fun change(transform: (OnboardingState) -> OnboardingState) {
        val next = transform(mutableState.value).copy(error = false)
        mutableState.value = next
        saved["units"] = next.units.name
        saved["dayStart"] = next.dayStart
        saved["bodyUnits"] = next.body.units.name
        saved["weight"] = next.body.weight
        saved["height"] = next.body.heightCm
        saved["heightFeet"] = next.body.heightFeet
        saved["heightInches"] = next.body.heightInches
        saved["wake"] = next.wake
        saved["sleep"] = next.sleep
        saved["remindersEnabled"] = next.remindersEnabled
        saved["notificationsGranted"] = next.notificationsGranted
        saved["condition"] = next.condition
        saved["choice"] = next.choice.name
        saved["birthDate"] = next.birthDate
        saved["gender"] = next.gender.name
        saved["activity"] = next.activity.name
        saved["climate"] = next.climate.name
        saved["pregnant"] = next.pregnant
        saved["lactating"] = next.lactating
        saved["altitude"] = next.altitude
        saved["manualGoal"] = next.manualGoal
    }

    fun persistStep(step: String) {
        val s = state.value
        viewModelScope.launch {
            settings.update { it.copy(units = s.units, bodyUnits = s.body.units, dayStartMinutes = s.dayStart) }
            device.update { it.copy(onboarding = it.onboarding.copy(lastStep = step, manualModeRecommended = s.condition)) }
        }
    }

    fun acceptDisclaimer() {
        viewModelScope.launch {
            device.update { it.copy(onboarding = it.onboarding.copy(disclaimerAcceptedAtMs = clock.millis())) }
        }
    }

    fun profile(): Profile? = runCatching {
        val s = state.value
        val date = LocalDate.parse(s.birthDate)
        val weight = requireNotNull(s.body.weightKg())
        val height = if (s.body.heightBlank) null else requireNotNull(s.body.heightCmValue())
        require(date <= LocalDate.now(clock))
        require(weight in 1.0..500.0)
        require(height == null || height in 30.0..300.0)
        Profile(
            date, s.gender, weight, height, s.activity, s.climate,
            if (s.pregnant) PregnancyStatus.PREGNANT else PregnancyStatus.NONE,
            s.lactating, s.altitude,
        )
    }.getOrNull()

    fun preview(): GoalBreakdown? = profile()?.let {
        calculator.calculate(
            GoalInput(
                currentHydrationDay(),
                it,
                ActivityInput.Level(it.activityLevel),
                ClimateInput.Manual(it.climate),
                casa.peterson.dhmo.core.model.settings.AdjustmentToggles(),
            ),
        )
    }

    private fun currentHydrationDay(): LocalDate {
        val now = clock.instant()
        val dayStart = LocalTime.ofSecondOfDay(state.value.dayStart * 60L)
        return hydrationDay(now, zone.current().rules.getOffset(now), dayStart)
    }

    /** Writes the state directly: [change] clears the error flag, which would hide the message at once. */
    fun validProfile(): Boolean = (profile() != null).also { if (!it) mutableState.value = state.value.copy(error = true) }
    fun validManualGoal(): Boolean = (state.value.manualGoal.toIntOrNull() in 250..10000).also {
        if (!it) {
            mutableState.value =
                state.value.copy(error = true)
        }
    }

    fun finish(onDone: () -> Unit) {
        val s = state.value
        if (s.busy) return
        mutableState.value = s.copy(busy = true)
        viewModelScope.launch {
            try {
                settings.update {
                    it.copy(
                        units = s.units,
                        bodyUnits = s.body.units,
                        dayStartMinutes = s.dayStart,
                        reminders = it.reminders.copy(
                            enabled =
                                s.remindersEnabled && s.notificationsGranted,
                            wakeMinutes = s.wake,
                            sleepMinutes = s.sleep,
                        ),
                    )
                }
                if (s.returning) {
                    containers.seedDefaultsIfEmpty(s.units)
                    device.update { it.copy(onboarding = it.onboarding.copy(completed = true, lastStep = null)) }
                } else {
                    complete(
                        if (s.choice ==
                            GoalChoice.CALCULATED
                        ) {
                            profile()
                        } else {
                            null
                        },
                        if (s.choice == GoalChoice.MANUAL) s.manualGoal.toIntOrNull() else null,
                    )
                }
                onDone()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = state.value.copy(busy = false, error = true)
            }
        }
    }
}

/** Imperial is the default only for a first run in the few regions that use it; afterwards the saved choice wins. */
private fun firstRunBodyUnits(returning: Boolean, saved: BodyUnits): BodyUnits =
    if (!returning && saved == BodyUnits.METRIC && java.util.Locale.getDefault().country in IMPERIAL_REGIONS) BodyUnits.IMPERIAL else saved

private val IMPERIAL_REGIONS = setOf("US", "LR", "MM")
