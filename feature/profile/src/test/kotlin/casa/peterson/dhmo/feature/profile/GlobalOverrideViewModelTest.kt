package casa.peterson.dhmo.feature.profile

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.settings.GlobalOverride
import casa.peterson.dhmo.core.model.settings.GoalSettings
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.core.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlobalOverrideViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun loadsStoredOverrideInUserUnits() = runTest {
        val stored = GlobalOverride(enabled = true, targetMl = 2500, perWeekdayMl = mapOf("SATURDAY" to 3000))
        val env = ProfileTestEnv(settings = AppSettings(units = VolumeUnit.L, goal = GoalSettings(globalOverride = stored)))
        val state = env.globalOverrideViewModel().state.value
        assertThat(state.loading).isFalse()
        assertThat(state.enabled).isTrue()
        assertThat(state.target.text).isEqualTo("2.5")
        assertThat(state.perWeekday.getValue(DayOfWeek.SATURDAY).text).isEqualTo("3")
        assertThat(state.perWeekday.getValue(DayOfWeek.MONDAY).text).isEmpty()
        assertThat(state.units).isEqualTo(VolumeUnit.L)
    }

    @Test
    fun savesEnabledOverrideWithWeekdayAndResnapshotsToday() = runTest {
        val env = ProfileTestEnv()
        val vm = env.globalOverrideViewModel()
        vm.saved.test {
            vm.onEnabledChange(true)
            vm.onTargetChange("2400")
            vm.onWeekdayChange(DayOfWeek.WEDNESDAY, "3100")
            vm.onSave()
            awaitItem()
        }
        val saved = env.settings.current().goal.globalOverride
        assertThat(saved).isEqualTo(GlobalOverride(enabled = true, targetMl = 2400, perWeekdayMl = mapOf("WEDNESDAY" to 3100)))
        // 2026-06-10 is a Wednesday, so the weekday value applies today.
        val today = env.goals.goal(env.today)!!
        assertThat(today.targetMl).isEqualTo(3100)
        assertThat(today.mode).isEqualTo(GoalMode.GLOBAL_OVERRIDE)
        assertThat(today.calculatedMl).isNotNull()
    }

    @Test
    fun rejectsValuesOutsideManualRange() = runTest {
        val env = ProfileTestEnv()
        val vm = env.globalOverrideViewModel()
        vm.onEnabledChange(true)
        vm.onTargetChange("249")
        vm.onSave()
        assertThat(vm.state.value.invalid).isTrue()

        vm.onTargetChange("2000")
        assertThat(vm.state.value.invalid).isFalse()
        vm.onWeekdayChange(DayOfWeek.MONDAY, "10001")
        vm.onSave()
        assertThat(vm.state.value.invalid).isTrue()

        vm.onWeekdayChange(DayOfWeek.MONDAY, "abc")
        vm.onSave()
        assertThat(vm.state.value.invalid).isTrue()
        assertThat(env.settings.current().goal.globalOverride).isEqualTo(GlobalOverride())
    }

    @Test
    fun ouncesConvertAndUntouchedValuesRoundTrip() = runTest {
        // 2,000 ml shows as 67.6 fl oz, which converts back to 1,999 ml; an untouched field must keep 2,000.
        val stored = GlobalOverride(enabled = false, targetMl = 2000, perWeekdayMl = mapOf("FRIDAY" to 2500))
        val env = ProfileTestEnv(settings = AppSettings(units = VolumeUnit.US_FL_OZ, goal = GoalSettings(globalOverride = stored)))
        val vm = env.globalOverrideViewModel()
        assertThat(vm.state.value.target.text).isEqualTo("67.6")
        vm.saved.test {
            vm.onEnabledChange(true)
            vm.onWeekdayChange(DayOfWeek.SUNDAY, "100")
            vm.onSave()
            awaitItem()
        }
        val saved = env.settings.current().goal.globalOverride
        assertThat(saved.targetMl).isEqualTo(2000)
        assertThat(saved.perWeekdayMl).containsExactly("FRIDAY", 2500, "SUNDAY", 2957)
    }

    @Test
    fun clearingWeekdayFallsBackToDailyGoal() = runTest {
        val stored = GlobalOverride(enabled = true, targetMl = 2500, perWeekdayMl = mapOf("SATURDAY" to 3000))
        val env = ProfileTestEnv(settings = AppSettings(goal = GoalSettings(globalOverride = stored)))
        val vm = env.globalOverrideViewModel()
        vm.saved.test {
            vm.onWeekdayChange(DayOfWeek.SATURDAY, " ")
            vm.onSave()
            awaitItem()
        }
        assertThat(env.settings.current().goal.globalOverride.perWeekdayMl).isEmpty()
    }

    @Test
    fun disablingKeepsTheCalculatedGoal() = runTest {
        val stored = GlobalOverride(enabled = true, targetMl = 2500)
        val env = ProfileTestEnv(settings = AppSettings(goal = GoalSettings(globalOverride = stored)))
        val vm = env.globalOverrideViewModel()
        vm.saved.test {
            vm.onEnabledChange(false)
            vm.onSave()
            awaitItem()
        }
        assertThat(env.settings.current().goal.globalOverride.enabled).isFalse()
        assertThat(env.goals.goal(env.today)!!.mode).isEqualTo(GoalMode.CALCULATED)
    }
}
