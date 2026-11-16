package casa.peterson.dhmo.feature.insights.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import casa.peterson.dhmo.feature.insights.CustomRangeScreen
import casa.peterson.dhmo.feature.insights.InsightsScreen
import casa.peterson.dhmo.feature.insights.correlation.CorrelationScreen
import java.time.LocalDate

fun NavGraphBuilder.insightsScreens(navController: NavController, onOpenDay: (LocalDate) -> Unit) {
    val openCorrelation: (String) -> Unit = { driver -> navController.navigate(CorrelationRoute(driver)) }
    composable<InsightsRoute> { InsightsScreen(onOpenDay = onOpenDay, onOpenCorrelation = openCorrelation) }
    composable<CustomRangeRoute> {
        CustomRangeScreen(onBack = { navController.popBackStack() }, onOpenDay = onOpenDay, onOpenCorrelation = openCorrelation)
    }
    composable<CorrelationRoute> { CorrelationScreen(onBack = { navController.popBackStack() }) }
}
