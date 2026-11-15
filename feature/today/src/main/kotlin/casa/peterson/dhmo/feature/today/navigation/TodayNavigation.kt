@file:Suppress("UNUSED_PARAMETER")

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
    composable<TodayRoute>(deepLinks = listOf(navDeepLink<TodayRoute>(basePath = "dhmo://today"))) { TodayScreen() }
    composable<EditEntryRoute> { EditEntryScreen() }
    composable<ContainersRoute> { ContainersScreen() }
    composable<BatteryHelpRoute> { BatteryHelpScreen() }
}
