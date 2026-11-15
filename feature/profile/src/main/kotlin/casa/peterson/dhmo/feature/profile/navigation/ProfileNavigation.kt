@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.feature.profile.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import casa.peterson.dhmo.feature.profile.AdjustmentsScreen
import casa.peterson.dhmo.feature.profile.DayOverrideScreen
import casa.peterson.dhmo.feature.profile.EditProfileScreen
import casa.peterson.dhmo.feature.profile.GlobalOverrideScreen
import casa.peterson.dhmo.feature.profile.ProfileGoalScreen
import casa.peterson.dhmo.feature.profile.SourcesScreen

fun NavGraphBuilder.profileScreens(navController: NavController) {
    composable<ProfileGoalRoute> { ProfileGoalScreen() }
    composable<EditProfileRoute> { EditProfileScreen() }
    composable<GlobalOverrideRoute> { GlobalOverrideScreen() }
    composable<DayOverrideRoute> { DayOverrideScreen() }
    composable<AdjustmentsRoute> { AdjustmentsScreen() }
    composable<SourcesRoute> { SourcesScreen() }
}
