@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.feature.insights.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import casa.peterson.dhmo.feature.insights.CustomRangeScreen
import casa.peterson.dhmo.feature.insights.InsightsScreen
import casa.peterson.dhmo.feature.insights.correlation.CorrelationScreen
import java.time.LocalDate

fun NavGraphBuilder.insightsScreens(navController: NavController, onOpenDay: (LocalDate) -> Unit) {
    composable<InsightsRoute> { InsightsScreen() }
    composable<CustomRangeRoute> { CustomRangeScreen() }
    composable<CorrelationRoute> { CorrelationScreen() }
}
