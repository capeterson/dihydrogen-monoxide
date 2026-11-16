package casa.peterson.dhmo.feature.reminders.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import casa.peterson.dhmo.feature.reminders.PaceCurveScreen
import casa.peterson.dhmo.feature.reminders.RemindersScreen

fun NavGraphBuilder.remindersScreens(navController: NavController) {
    composable<RemindersRoute> {
        RemindersScreen(onBack = { navController.popBackStack() }, onOpenCurve = { navController.navigate(PaceCurveRoute) })
    }
    composable<PaceCurveRoute> { PaceCurveScreen(onBack = { navController.popBackStack() }) }
}
