package casa.peterson.dhmo.feature.profile.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import casa.peterson.dhmo.feature.profile.AdjustmentsScreen
import casa.peterson.dhmo.feature.profile.DayOverrideScreen
import casa.peterson.dhmo.feature.profile.EditProfileScreen
import casa.peterson.dhmo.feature.profile.GlobalOverrideScreen
import casa.peterson.dhmo.feature.profile.ProfileGoalScreen
import casa.peterson.dhmo.feature.profile.SourcesScreen

fun NavGraphBuilder.profileScreens(navController: NavController) {
    val back: () -> Unit = { navController.popBackStack() }
    composable<ProfileGoalRoute> {
        ProfileGoalScreen(
            onBack = back,
            onEditProfile = { navController.navigate(EditProfileRoute) },
            onGlobalOverride = { navController.navigate(GlobalOverrideRoute) },
            onDayOverride = { navController.navigate(DayOverrideRoute(it.toEpochDay())) },
            onAdjustments = { navController.navigate(AdjustmentsRoute) },
            onSources = { navController.navigate(SourcesRoute) },
        )
    }
    composable<EditProfileRoute> { EditProfileScreen(onBack = back) }
    composable<GlobalOverrideRoute> { GlobalOverrideScreen(onBack = back) }
    composable<DayOverrideRoute> { entry ->
        if (entry.toRoute<DayOverrideRoute>().date == null) {
            LaunchedEffect(entry) { navController.popBackStack(entry.destination.id, inclusive = true) }
        } else {
            DayOverrideScreen(onBack = back)
        }
    }
    composable<AdjustmentsRoute> { AdjustmentsScreen(onBack = back) }
    composable<SourcesRoute> { SourcesScreen(onBack = back) }
}
