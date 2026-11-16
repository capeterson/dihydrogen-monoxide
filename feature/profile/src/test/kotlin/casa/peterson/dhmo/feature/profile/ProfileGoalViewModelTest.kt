package casa.peterson.dhmo.feature.profile

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import casa.peterson.dhmo.core.model.ActivityLevel
import casa.peterson.dhmo.core.model.ActivitySource
import casa.peterson.dhmo.core.model.ClampFlag
import casa.peterson.dhmo.core.model.Climate
import casa.peterson.dhmo.core.model.ComponentKind
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.model.Intensity
import casa.peterson.dhmo.core.model.PregnancyStatus
import casa.peterson.dhmo.core.model.Profile
import casa.peterson.dhmo.core.model.Sex
import casa.peterson.dhmo.core.model.settings.AdjustmentToggles
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.settings.GlobalOverride
import casa.peterson.dhmo.core.model.settings.GoalSettings
import casa.peterson.dhmo.core.model.settings.PinnedInputs
import casa.peterson.dhmo.core.model.settings.WeatherMode
import casa.peterson.dhmo.core.model.settings.WeatherSettings
import casa.peterson.dhmo.core.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileGoalViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    /** G06: male, 31, 70 kg, sedentary, temperate → 2,310 ml raw, 2,300 ml goal (TECHNICAL_PLAN §7.5). */
    private val g06 = Profile(
        birthDate = LocalDate.of(1995, 1, 1),
        sex = Sex.MALE,
        weightKg = 70.0,
        heightCm = null,
        activityLevel = ActivityLevel.SEDENTARY,
        climate = Climate.TEMPERATE,
        pregnancyStatus = PregnancyStatus.NONE,
        lactating = false,
        highAltitude = false,
    )

    private suspend fun ReceiveTurbine<ProfileGoalUiState>.awaitReady(
        predicate: (ProfileGoalUiState.Ready) -> Boolean = { true },
    ): ProfileGoalUiState.Ready {
        while (true) {
            val item = awaitItem()
            if (item is ProfileGoalUiState.Ready && predicate(item)) return item
        }
    }

    @Test
    fun calculatedGoalShowsBreakdown() = runTest {
        val env = ProfileTestEnv(g06)
        env.profileGoalViewModel().uiState.test {
            val ready = awaitReady()
            assertThat(ready.day).isEqualTo(env.today)
            assertThat(ready.effective.targetMl).isEqualTo(2300)
            assertThat(ready.effective.calculatedMl).isEqualTo(2300)
            assertThat(ready.effective.mode).isEqualTo(GoalMode.CALCULATED)
            val breakdown = checkNotNull(ready.breakdown)
            assertThat(breakdown.components.map { it.kind })
                .containsExactly(ComponentKind.BASE_WEIGHT, ComponentKind.ACTIVITY_LEVEL, ComponentKind.CLIMATE_MANUAL)
                .inOrder()
            assertThat(breakdown.rawMl).isEqualTo(2310)
            assertThat(ready.profile).isEqualTo(g06)
            assertThat(ready.forecastUnavailable).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun globalOverrideKeepsCalculatedGoalVisible() = runTest {
        val settings = AppSettings(goal = GoalSettings(globalOverride = GlobalOverride(enabled = true, targetMl = 2500)))
        val env = ProfileTestEnv(g06, settings)
        env.profileGoalViewModel().uiState.test {
            val ready = awaitReady()
            assertThat(ready.effective.targetMl).isEqualTo(2500)
            assertThat(ready.effective.calculatedMl).isEqualTo(2300)
            assertThat(ready.effective.mode).isEqualTo(GoalMode.GLOBAL_OVERRIDE)
            assertThat(ready.breakdown).isNotNull()
            assertThat(ready.globalOverride.enabled).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun dayOverrideAndClear() = runTest {
        val env = ProfileTestEnv(g06)
        env.goals.setDayOverride(env.today, 4000, "hike")
        val vm = env.profileGoalViewModel()
        vm.uiState.test {
            val ready = awaitReady()
            assertThat(ready.effective.targetMl).isEqualTo(4000)
            assertThat(ready.effective.mode).isEqualTo(GoalMode.DAY_OVERRIDE)
            assertThat(ready.effective.calculatedMl).isEqualTo(2300)
            assertThat(ready.todayOverride?.reason).isEqualTo("hike")
            vm.onClearTodayOverride()
            val cleared = awaitReady { it.todayOverride == null && it.effective.mode == GoalMode.CALCULATED }
            assertThat(cleared.effective.targetMl).isEqualTo(2300)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun clampFlagIsExposed() = runTest {
        // G27: 22-year-old, 160 kg, very active, high altitude → 6,000 ml with the HIGH clamp.
        val profile = g06.copy(
            birthDate = LocalDate.of(2004, 1, 1),
            weightKg = 160.0,
            activityLevel = ActivityLevel.VERY_ACTIVE,
            highAltitude = true,
        )
        val env = ProfileTestEnv(profile)
        env.profileGoalViewModel().uiState.test {
            val ready = awaitReady()
            val breakdown = checkNotNull(ready.breakdown)
            assertThat(breakdown.clamp).isEqualTo(ClampFlag.HIGH)
            assertThat(breakdown.clampedMl).isEqualTo(6000)
            assertThat(ready.effective.targetMl).isEqualTo(6000)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun plannedExerciseReplacesActivityLevel() = runTest {
        val env = ProfileTestEnv(g06)
        val vm = env.profileGoalViewModel()
        vm.uiState.test {
            awaitReady()
            assertThat(vm.onAddPlanned(30, Intensity.MODERATE)).isTrue()
            val ready = awaitReady { state -> state.breakdown?.components?.any { it.kind == ComponentKind.ACTIVITY_MINUTES } == true }
            assertThat(ready.planned).hasSize(1)
            assertThat(ready.planned.single().source).isEqualTo(ActivitySource.MANUAL_PLANNED)
            assertThat(ready.planned.single().date).isEqualTo(env.today)
            val minutes = checkNotNull(ready.breakdown).components.single { it.kind == ComponentKind.ACTIVITY_MINUTES }
            assertThat(minutes.ml).isEqualTo(360)
            assertThat(ready.effective.targetMl).isEqualTo(2650) // 2,310 + 360 = 2,670 → 2,650

            vm.onRemovePlanned(ready.planned.single().sourceRecordId)
            val removed = awaitReady { it.planned.isEmpty() && it.effective.targetMl == 2300 }
            assertThat(checkNotNull(removed.breakdown).components.map { it.kind }).contains(ComponentKind.ACTIVITY_LEVEL)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun plannedMinutesOutOfRangeAreRejected() = runTest {
        val env = ProfileTestEnv(g06)
        val vm = env.profileGoalViewModel()
        assertThat(vm.onAddPlanned(0, Intensity.LIGHT)).isFalse()
        assertThat(vm.onAddPlanned(601, Intensity.LIGHT)).isFalse()
        assertThat(env.activity.forDay(env.today)).isEmpty()
    }

    @Test
    fun missingForecastIsFlagged() = runTest {
        val env = ProfileTestEnv(g06, AppSettings(weather = WeatherSettings(mode = WeatherMode.MANUAL_CITY)))
        env.profileGoalViewModel().uiState.test {
            val ready = awaitReady()
            assertThat(ready.forecastUnavailable).isTrue()
            assertThat(ready.weatherMode).isEqualTo(WeatherMode.MANUAL_CITY)
            assertThat(ready.weather).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun noProfileHasNoBreakdown() = runTest {
        val settings = AppSettings(goal = GoalSettings(globalOverride = GlobalOverride(enabled = true, targetMl = 1800)))
        val env = ProfileTestEnv(profile = null, settings = settings)
        env.profileGoalViewModel().uiState.test {
            val ready = awaitReady()
            assertThat(ready.profile).isNull()
            assertThat(ready.breakdown).isNull()
            assertThat(ready.effective.targetMl).isEqualTo(1800)
            assertThat(ready.effective.calculatedMl).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun adjustmentTogglesAndPinsResnapshotToday() = runTest {
        val env = ProfileTestEnv(g06.copy(activityLevel = ActivityLevel.MODERATE))
        val adjustments = env.adjustmentsViewModel()
        adjustments.setToggles(AdjustmentToggles(activity = false))
        assertThat(env.settings.current().goal.adjustments.activity).isFalse()
        val goal = env.goals.goal(env.today)!!
        assertThat(goal.breakdown.components.single { it.kind == ComponentKind.ACTIVITY_LEVEL }.applied).isFalse()
        assertThat(goal.targetMl).isEqualTo(2300)

        env.activity.addPlanned(env.today, 30, Intensity.MODERATE)
        adjustments.setPinned(PinnedInputs(pinActivityLevel = true))
        assertThat(env.settings.current().goal.pinned.pinActivityLevel).isTrue()
        assertThat(env.goals.goal(env.today)!!.breakdown.components.map { it.kind }).contains(ComponentKind.ACTIVITY_LEVEL)
    }
}
