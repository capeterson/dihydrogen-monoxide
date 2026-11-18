package casa.peterson.dhmo.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import casa.peterson.dhmo.feature.history.navigation.HistoryDayRoute
import casa.peterson.dhmo.feature.history.navigation.historyScreens
import casa.peterson.dhmo.feature.insights.navigation.insightsScreens
import casa.peterson.dhmo.feature.onboarding.navigation.OnboardingGraph
import casa.peterson.dhmo.feature.onboarding.navigation.OnboardingIntegrations
import casa.peterson.dhmo.feature.onboarding.navigation.onboardingGraph
import casa.peterson.dhmo.feature.profile.navigation.ProfileGoalRoute
import casa.peterson.dhmo.feature.profile.navigation.profileScreens
import casa.peterson.dhmo.feature.reminders.navigation.RemindersRoute
import casa.peterson.dhmo.feature.reminders.navigation.remindersScreens
import casa.peterson.dhmo.feature.settings.navigation.SettingsIntegrations
import casa.peterson.dhmo.feature.settings.navigation.settingsScreens
import casa.peterson.dhmo.feature.today.navigation.EditEntryRoute
import casa.peterson.dhmo.feature.today.navigation.TodayRoute
import casa.peterson.dhmo.feature.today.navigation.todayScreens
import casa.peterson.dhmo.sync.drive.DriveAvailability
import casa.peterson.dhmo.sync.drive.ui.DriveBackupSection
import casa.peterson.dhmo.sync.drive.ui.DriveRestoreOffer
import casa.peterson.dhmo.sync.drive.ui.DriveRestoreScreen
import casa.peterson.dhmo.sync.healthconnect.ui.HealthConnectSettingsScreen

/**
 * Calls every feature entry function. Cross-feature navigation is passed in as lambdas so features stay
 * unaware of each other. Later phases replace the default `SettingsIntegrations()` arguments (Drive, Health
 * Connect, weather permission) and the `OnboardingIntegrations` slots here.
 */
@Composable
fun DhmoNavHost(navController: NavHostController, onboarded: Boolean, modifier: Modifier = Modifier) {
    val openGoal = { navController.navigate(ProfileGoalRoute) }
    val openReminders = { navController.navigate(RemindersRoute) }
    NavHost(
        navController = navController,
        startDestination = if (onboarded) TodayRoute else OnboardingGraph,
        modifier = modifier,
    ) {
        onboardingGraph(
            navController = navController,
            onFinished = {
                navController.navigate(TodayRoute) { popUpTo(OnboardingGraph) { inclusive = true } }
            },
            integrations = OnboardingIntegrations(
                restoreOffer = if (DriveAvailability.isConfigured) {
                    { onSkip, onRestored -> DriveRestoreOffer(onSkip, onRestored) }
                } else {
                    null
                },
            ),
        )
        todayScreens(
            navController = navController,
            onOpenGoal = openGoal,
            onOpenReminders = openReminders,
            onOpenHistoryDay = { navController.navigate(HistoryDayRoute(it.toEpochDay())) },
        )
        historyScreens(navController = navController, onEditEntry = { navController.navigate(EditEntryRoute(it.toString())) })
        insightsScreens(navController = navController, onOpenDay = { navController.navigate(HistoryDayRoute(it.toEpochDay())) })
        profileScreens(navController)
        remindersScreens(navController)
        settingsScreens(
            navController = navController,
            onOpenProfile = openGoal,
            onOpenReminders = openReminders,
            integrations = SettingsIntegrations(
                driveBackupSection = { onOpenRestore -> DriveBackupSection(onOpenRestore) },
                driveRestoreContent = { onBack -> DriveRestoreScreen(onBack) },
                healthConnectContent = { onBack -> HealthConnectSettingsScreen(onBack) },
            ),
        )
    }
}
