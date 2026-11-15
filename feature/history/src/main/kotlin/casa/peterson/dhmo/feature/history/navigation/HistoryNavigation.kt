@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.feature.history.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import casa.peterson.dhmo.feature.history.HistoryDayScreen
import casa.peterson.dhmo.feature.history.HistoryScreen
import java.util.UUID

/** Deep link: `dhmo://history/day/{epochDay}`. */
fun NavGraphBuilder.historyScreens(navController: NavController, onEditEntry: (UUID) -> Unit) {
    composable<HistoryRoute> { HistoryScreen() }
    composable<HistoryDayRoute>(deepLinks = listOf(navDeepLink<HistoryDayRoute>(basePath = "dhmo://history/day"))) {
        HistoryDayScreen()
    }
}
