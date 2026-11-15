@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.feature.onboarding.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import casa.peterson.dhmo.feature.onboarding.DisclaimerScreen
import casa.peterson.dhmo.feature.onboarding.GoalModeScreen
import casa.peterson.dhmo.feature.onboarding.GoalPreviewScreen
import casa.peterson.dhmo.feature.onboarding.ManualGoalScreen
import casa.peterson.dhmo.feature.onboarding.NotificationRationaleScreen
import casa.peterson.dhmo.feature.onboarding.OnboardingDoneScreen
import casa.peterson.dhmo.feature.onboarding.ProfileSetupScreen
import casa.peterson.dhmo.feature.onboarding.ReminderSetupScreen
import casa.peterson.dhmo.feature.onboarding.UnitsScreen
import casa.peterson.dhmo.feature.onboarding.WelcomeScreen

/** The onboarding flow. [onFinished] is called when the user leaves `OnboardingDoneRoute`. */
fun NavGraphBuilder.onboardingGraph(
    navController: NavController,
    onFinished: () -> Unit,
    integrations: OnboardingIntegrations = OnboardingIntegrations(),
) {
    navigation<OnboardingGraph>(startDestination = WelcomeRoute) {
        composable<WelcomeRoute> { WelcomeScreen() }
        composable<DisclaimerRoute> { DisclaimerScreen() }
        composable<UnitsRoute> { UnitsScreen() }
        composable<GoalModeRoute> { GoalModeScreen() }
        composable<ProfileSetupRoute> { ProfileSetupScreen() }
        composable<ManualGoalRoute> { ManualGoalScreen() }
        composable<GoalPreviewRoute> { GoalPreviewScreen() }
        composable<NotificationRationaleRoute> { NotificationRationaleScreen() }
        composable<ReminderSetupRoute> { ReminderSetupScreen() }
        composable<RestoreOfferRoute> { integrations.restoreOffer { navController.popBackStack() } }
        composable<WeatherRationaleRoute> { integrations.weatherRationale { navController.popBackStack() } }
        composable<OnboardingDoneRoute> { OnboardingDoneScreen() }
    }
}
