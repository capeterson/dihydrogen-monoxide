package casa.peterson.dhmo.feature.onboarding

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.data.time.DayTicker
import casa.peterson.dhmo.core.data.usecase.goal.BuildGoalInputUseCase
import casa.peterson.dhmo.core.data.usecase.goal.SnapshotDailyGoalUseCase
import casa.peterson.dhmo.core.data.usecase.onboarding.CompleteOnboardingUseCase
import casa.peterson.dhmo.core.domain.body.BodyInputs
import casa.peterson.dhmo.core.domain.goal.GoalCalculator
import casa.peterson.dhmo.core.domain.goal.GoalPolicyV1
import casa.peterson.dhmo.core.model.ActivityInput
import casa.peterson.dhmo.core.model.ClimateInput
import casa.peterson.dhmo.core.model.Gender
import casa.peterson.dhmo.core.model.GoalInput
import casa.peterson.dhmo.core.model.settings.AdjustmentToggles
import casa.peterson.dhmo.core.model.settings.BodyUnits
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.core.testing.FakeActivityRepository
import casa.peterson.dhmo.core.testing.FakeContainerRepository
import casa.peterson.dhmo.core.testing.FakeDeviceStateRepository
import casa.peterson.dhmo.core.testing.FakeGoalRepository
import casa.peterson.dhmo.core.testing.FakeHealthConnectController
import casa.peterson.dhmo.core.testing.FakeProfileRepository
import casa.peterson.dhmo.core.testing.FakeReminderScheduler
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.FakeWeatherRepository
import casa.peterson.dhmo.core.testing.FixedZoneProvider
import casa.peterson.dhmo.core.testing.MainDispatcherRule
import casa.peterson.dhmo.core.testing.MutableClock
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val clock = MutableClock(Instant.parse("2026-06-10T12:00:00Z"))
    private val zone = FixedZoneProvider(ZoneOffset.UTC)
    private val settings = FakeSettingsRepository()
    private val device = FakeDeviceStateRepository()
    private val profiles: FakeProfileRepository = FakeProfileRepository(clock = clock)
    private val containers = FakeContainerRepository()
    private val goals = FakeGoalRepository(clock)
    private val ticker = DayTicker(ApplicationProvider.getApplicationContext<Context>(), clock, zone, settings)
    private val calculator = GoalCalculator()
    private val input: BuildGoalInputUseCase = BuildGoalInputUseCase(
        profiles,
        settings,
        FakeActivityRepository(clock),
        FakeWeatherRepository(),
        FakeHealthConnectController(),
    )
    private val snapshot = SnapshotDailyGoalUseCase(input, calculator, GoalPolicyV1, goals, settings, FakeReminderScheduler(), clock)
    private val complete = CompleteOnboardingUseCase(profiles, settings, containers, device, ticker, snapshot)
    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        OnboardingViewModel(settings, device, profiles, containers, complete, calculator, clock, zone, saved)

    @Test
    fun manualGoalFollowsSelectedUnits() {
        val oz = OnboardingState(units = VolumeUnit.US_FL_OZ)
        assertThat(oz.manualGoalText()).isEqualTo("67.63")
        assertThat(OnboardingState(manualGoal = "1000").withUnits(VolumeUnit.L).manualGoalText()).isEqualTo("1")
        assertThat(OnboardingState(units = VolumeUnit.L, manualGoal = "1.5").manualGoalMl()).isEqualTo(1500)
    }

    @Test
    fun manualPathPersistsGoalAndCompletion() = runTest {
        val vm = viewModel()
        vm.change { it.copy(condition = true, choice = GoalChoice.MANUAL, manualGoal = "1800") }
        assertThat(vm.validManualGoal()).isTrue()
        var finished = false
        vm.finish { finished = true }
        assertThat(finished).isTrue()
        assertThat(settings.current().goal.globalOverride.targetMl).isEqualTo(1800)
        assertThat(settings.current().goal.globalOverride.enabled).isTrue()
        assertThat(containers.getAll()).hasSize(3)
        assertThat(device.current().onboarding.completed).isTrue()
    }

    @Test
    fun imperialEntriesAreStoredAsKilogramsAndCentimetres() = runTest {
        val vm = viewModel()
        vm.change {
            it.copy(birthDate = "1990-05-17", body = BodyInputs(BodyUnits.IMPERIAL, "143.3", heightFeet = "5", heightInches = "6"))
        }
        val profile = checkNotNull(vm.profile())
        assertThat(profile.weightKg).isWithin(0.05).of(65.0)
        assertThat(profile.heightCm).isWithin(0.05).of(167.64)
        vm.finish {}
        assertThat(settings.current().bodyUnits).isEqualTo(BodyUnits.IMPERIAL)
    }

    @Test
    fun switchingUnitsConvertsWhatWasTyped() = runTest {
        val vm = viewModel()
        vm.change { it.copy(body = BodyInputs(weight = "70", heightCm = "175")) }
        vm.change { it.copy(body = it.body.switchTo(BodyUnits.IMPERIAL)) }
        assertThat(vm.state.value.body.weight).isEqualTo("154.3")
        assertThat(vm.state.value.body.heightFeet).isEqualTo("5")
        vm.change { it.copy(body = it.body.switchTo(BodyUnits.METRIC)) }
        assertThat(vm.state.value.body.weight).isEqualTo("70")
        assertThat(vm.state.value.body.heightCm).isEqualTo("175")
    }

    @Test
    fun nonBinaryIsAcceptedAsAGender() = runTest {
        val vm = viewModel()
        vm.change { it.copy(birthDate = "1990-05-17", body = BodyInputs(weight = "65"), gender = Gender.NON_BINARY) }
        assertThat(checkNotNull(vm.profile()).gender).isEqualTo(Gender.NON_BINARY)
    }

    @Test
    fun invalidProfileShowsAnErrorUntilTheNextEdit() = runTest {
        val vm = viewModel()
        assertThat(vm.validProfile()).isFalse()
        assertThat(vm.state.value.error).isTrue()
        vm.change { it.copy(birthDate = "1990-05-17") }
        assertThat(vm.state.value.error).isFalse()
    }

    @Test
    fun calculatedPreviewMatchesCalculatorAndSavesProfile() = runTest {
        val vm = viewModel()
        vm.change { it.copy(birthDate = "1990-05-17", body = BodyInputs(weight = "65", heightCm = "168")) }
        val profile = checkNotNull(vm.profile())
        val expected = calculator.calculate(
            GoalInput(
                LocalDate.of(2026, 6, 10),
                profile,
                ActivityInput.Level(profile.activityLevel),
                ClimateInput.Manual(profile.climate),
                AdjustmentToggles(),
            ),
        )
        assertThat(vm.preview()).isEqualTo(expected)
        var finished = false
        vm.finish { finished = true }
        assertThat(finished).isTrue()
        assertThat(profiles.observeCurrent().first()).isEqualTo(profile)
        assertThat(device.current().onboarding.completed).isTrue()
    }

    @Test
    fun selectionsSurviveViewModelRecreation() {
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        first.change { it.copy(units = VolumeUnit.L, dayStart = 240, wake = 480, sleep = 1320, remindersEnabled = true) }
        val restored = viewModel(saved).state.value
        assertThat(restored.units).isEqualTo(VolumeUnit.L)
        assertThat(restored.dayStart).isEqualTo(240)
        assertThat(restored.wake).isEqualTo(480)
        assertThat(restored.sleep).isEqualTo(1320)
        assertThat(restored.remindersEnabled).isTrue()
    }
}
