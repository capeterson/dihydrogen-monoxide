package casa.peterson.dhmo.feature.today.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import casa.peterson.dhmo.feature.today.BatteryHelpScreen
import casa.peterson.dhmo.feature.today.ContainersScreen
import casa.peterson.dhmo.feature.today.EditEntryScreen
import casa.peterson.dhmo.feature.today.TodayScreen
import java.time.LocalDate

/** Deep link: `dhmo://today` (app shortcuts and the Quick Settings tile's long press). */
fun NavGraphBuilder.todayScreens(
    navController: NavController,
    onOpenGoal: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenHistoryDay: (LocalDate) -> Unit,
) {
    composable<TodayRoute>(deepLinks = listOf(navDeepLink<TodayRoute>(basePath = "dhmo://today"))) {
        TodayScreen(
            onOpenGoal = onOpenGoal,
            onOpenReminders = onOpenReminders,
            onOpenHistoryDay = onOpenHistoryDay,
            onEdit = { navController.navigate(EditEntryRoute(it.toString())) },
            onContainers = { navController.navigate(ContainersRoute) },
            onBatteryHelp = { navController.navigate(BatteryHelpRoute) },
        )
    }
    composable<EditEntryRoute> { EditEntryScreen(onDismiss = { navController.popBackStack() }) }
    composable<ContainersRoute> { ContainersScreen(onBack = { navController.popBackStack() }) }
    composable<BatteryHelpRoute> { BatteryHelpScreen(onBack = { navController.popBackStack() }) }
}
