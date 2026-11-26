package casa.peterson.dhmo.feature.onboarding

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.data.time.DayTicker
import casa.peterson.dhmo.core.data.usecase.goal.BuildGoalInputUseCase
import casa.peterson.dhmo.core.data.usecase.goal.SnapshotDailyGoalUseCase
import casa.peterson.dhmo.core.data.usecase.onboarding.CompleteOnboardingUseCase
import casa.peterson.dhmo.core.domain.goal.GoalCalculator
import casa.peterson.dhmo.core.domain.goal.GoalPolicyV1
import casa.peterson.dhmo.core.testing.FakeActivityRepository
import casa.peterson.dhmo.core.testing.FakeContainerRepository
import casa.peterson.dhmo.core.testing.FakeDeviceStateRepository
import casa.peterson.dhmo.core.testing.FakeGoalRepository
import casa.peterson.dhmo.core.testing.FakeHealthConnectController
import casa.peterson.dhmo.core.testing.FakeProfileRepository
import casa.peterson.dhmo.core.testing.FakeReminderScheduler
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.FakeWeatherRepository
import casa.peterson.dhmo.core.testing.FakeWidgetUpdater
import casa.peterson.dhmo.core.testing.FixedZoneProvider
import casa.peterson.dhmo.core.testing.MainDispatcherRule
import casa.peterson.dhmo.core.testing.MutableClock
import casa.peterson.dhmo.feature.onboarding.navigation.LocalOnboardingViewModel
import casa.peterson.dhmo.feature.onboarding.navigation.OnboardingGraph
import casa.peterson.dhmo.feature.onboarding.navigation.onboardingGraph
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingNavigationTest {
    @get:Rule(order = 0)
    val main = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    @Test
    fun navigationMovesForwardAndBack() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clock = MutableClock(Instant.parse("2026-06-10T12:00:00Z"))
        val zone = FixedZoneProvider(ZoneOffset.UTC)
        val settings = FakeSettingsRepository()
        val device = FakeDeviceStateRepository()
        val profiles: FakeProfileRepository = FakeProfileRepository(clock = clock)
        val containers = FakeContainerRepository()
        val ticker = DayTicker(context, clock, zone, settings)
        val calculator = GoalCalculator()
        val input =
            BuildGoalInputUseCase(profiles, settings, FakeActivityRepository(clock), FakeWeatherRepository(), FakeHealthConnectController())
        val snapshot =
            SnapshotDailyGoalUseCase(
                input,
                calculator,
                GoalPolicyV1,
                FakeGoalRepository(clock),
                settings,
                FakeReminderScheduler(),
                clock,
                FakeWidgetUpdater(),
            )
        val complete = CompleteOnboardingUseCase(profiles, settings, containers, device, ticker, snapshot)
        val vm = OnboardingViewModel(settings, device, profiles, containers, complete, calculator, clock, zone, SavedStateHandle())
        val nav = TestNavHostController(context).apply { navigatorProvider.addNavigator(ComposeNavigator()) }
        compose.setContent {
            val owner = LocalLifecycleOwner.current
            SideEffect { nav.setLifecycleOwner(owner) }
            MaterialTheme {
                CompositionLocalProvider(LocalOnboardingViewModel provides vm) {
                    NavHost(nav, startDestination = OnboardingGraph) { onboardingGraph(nav, onFinished = {}) }
                }
            }
        }
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Before you begin").assertExists()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Units and day start").assertExists()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Back").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { assertThat(nav.currentBackStackEntry?.destination?.route).contains("DisclaimerRoute") }
        compose.onNodeWithText("Before you begin").assertExists()
    }
}
