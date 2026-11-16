package casa.peterson.dhmo.feature.profile

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import casa.peterson.dhmo.core.data.usecase.goal.ClearDayOverrideUseCase
import casa.peterson.dhmo.core.data.usecase.goal.SetDayOverrideUseCase
import casa.peterson.dhmo.core.model.ActivityLevel
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.model.PregnancyStatus
import casa.peterson.dhmo.core.model.Sex
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.core.testing.MainDispatcherRule
import casa.peterson.dhmo.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileEditorsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private fun ProfileTestEnv.dayOverrideViewModel(epochDay: Long) = DayOverrideViewModel(
        SavedStateHandle(mapOf("epochDay" to epochDay)),
        goals,
        settings,
        SetDayOverrideUseCase(goals, ticker, snapshot),
        ClearDayOverrideUseCase(goals, ticker, snapshot),
    )

    @Test
    fun dayOverrideSetsAndClears() = runTest {
        val env = ProfileTestEnv()
        env.snapshot(env.today)
        val calculated = env.goals.goal(env.today)!!.targetMl
        val vm = env.dayOverrideViewModel(env.today.toEpochDay())
        assertThat(vm.state.value.withoutOverrideMl).isEqualTo(calculated)
        assertThat(vm.state.value.target.text).isEqualTo(calculated.toString())
        vm.done.test {
            vm.onTargetChange("4025")
            vm.onReasonChange("  long hike ")
            vm.onSave()
            awaitItem()
        }
        val override = env.goals.observeDayOverride(env.today).first()!!
        assertThat(override.targetMl).isEqualTo(4025)
        assertThat(override.reason).isEqualTo("long hike")
        assertThat(env.goals.goal(env.today)!!.mode).isEqualTo(GoalMode.DAY_OVERRIDE)

        val again = env.dayOverrideViewModel(env.today.toEpochDay())
        assertThat(again.state.value.hasOverride).isTrue()
        assertThat(again.state.value.withoutOverrideMl).isEqualTo(calculated)
        again.done.test {
            again.onClear()
            awaitItem()
        }
        assertThat(env.goals.observeDayOverride(env.today).first()).isNull()
        assertThat(env.goals.goal(env.today)!!.targetMl).isEqualTo(calculated)
    }

    @Test
    fun dayOverrideValidatesRangeAndDay() = runTest {
        val env = ProfileTestEnv(settings = AppSettings(units = VolumeUnit.US_FL_OZ))
        val vm = env.dayOverrideViewModel(env.today.toEpochDay())
        vm.onTargetChange("8")
        vm.onSave()
        assertThat(vm.state.value.invalid).isTrue()
        assertThat(env.goals.observeDayOverride(env.today).first()).isNull()

        val outOfRange = env.dayOverrideViewModel(Long.MAX_VALUE)
        assertThat(outOfRange.state.value.day).isNull()
        outOfRange.onSave()
        assertThat(outOfRange.state.value.loading).isTrue()
    }

    @Test
    fun editProfileLoadsAndSaves() = runTest {
        val env = ProfileTestEnv()
        val vm = env.editProfileViewModel()
        val loaded = vm.state.value
        assertThat(loaded.birthDate).isEqualTo(TestData.adultProfile.birthDate.toString())
        assertThat(loaded.weight).isEqualTo("65")
        assertThat(loaded.height).isEqualTo("168")
        assertThat(loaded.activity).isEqualTo(ActivityLevel.MODERATE)
        vm.saved.test {
            vm.change { it.copy(weight = "72,5", height = "", sex = Sex.UNSPECIFIED, pregnant = true, highAltitude = true) }
            vm.onSave()
            awaitItem()
        }
        val saved = env.profiles.observeCurrent().first()!!
        assertThat(saved.weightKg).isEqualTo(72.5)
        assertThat(saved.heightCm).isNull()
        assertThat(saved.sex).isEqualTo(Sex.UNSPECIFIED)
        assertThat(saved.pregnancyStatus).isEqualTo(PregnancyStatus.PREGNANT)
        assertThat(saved.highAltitude).isTrue()
        assertThat(env.profiles.observeSnapshots().first().maxBy { it.createdAt }.effectiveFrom).isEqualTo(env.today)
        assertThat(env.goals.goal(env.today)!!.breakdown.components.map { it.kind.name }).contains("PREGNANCY")
    }

    @Test
    fun editProfileRejectsInvalidInput() = runTest {
        val env = ProfileTestEnv(profile = null)
        val vm = env.editProfileViewModel()
        assertThat(vm.state.value.birthDate).isEmpty()
        listOf(
            vm.state.value.copy(birthDate = "1990-13-01", weight = "70"),
            vm.state.value.copy(birthDate = LocalDate.of(2027, 1, 1).toString(), weight = "70"),
            vm.state.value.copy(birthDate = "1990-01-01", weight = "1"),
            vm.state.value.copy(birthDate = "1990-01-01", weight = "70", height = "500"),
        ).forEach { invalid ->
            vm.change { invalid }
            vm.onSave()
            assertThat(vm.state.value.invalid).isTrue()
        }
        assertThat(env.profiles.observeCurrent().first()).isNull()
    }
}
