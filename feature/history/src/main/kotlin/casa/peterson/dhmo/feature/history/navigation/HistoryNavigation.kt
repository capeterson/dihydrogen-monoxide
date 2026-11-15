package casa.peterson.dhmo.feature.history.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import casa.peterson.dhmo.feature.history.HistoryDayScreen
import casa.peterson.dhmo.feature.history.HistoryScreen
import java.time.LocalDate
import java.util.UUID

/** Deep link: `dhmo://history/day/{epochDay}`. */
fun NavGraphBuilder.historyScreens(navController: NavController, onEditEntry: (UUID) -> Unit) {
    composable<HistoryRoute> {
        HistoryScreen(onOpenDay = { navController.navigate(HistoryDayRoute(it.toEpochDay())) }, onEditEntry = onEditEntry)
    }
    composable<HistoryDayRoute>(deepLinks = listOf(navDeepLink<HistoryDayRoute>(basePath = "dhmo://history/day"))) { entry ->
        val day = LocalDate.ofEpochDay(entry.toRoute<HistoryDayRoute>().epochDay)
        HistoryDayScreen(day, onBack = { navController.popBackStack() }, onEditEntry = onEditEntry)
    }
}
