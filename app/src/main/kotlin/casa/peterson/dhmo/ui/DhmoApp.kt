package casa.peterson.dhmo.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.util.Consumer
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import casa.peterson.dhmo.StartState
import casa.peterson.dhmo.feature.onboarding.navigation.OnboardingGraph

/**
 * The app shell: bottom bar on compact width, navigation rail on larger widths, hidden during onboarding.
 *
 * Deep links (`dhmo://…`) are followed only outside onboarding: [launchDeepLink] is the one the activity was started
 * with, and later ones arrive through the activity's new-intent listener (it is `singleTop`).
 */
@Composable
fun DhmoApp(startState: StartState, launchDeepLink: Intent? = null) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val hierarchy = backStackEntry?.destination?.hierarchy
    val inOnboarding = hierarchy?.any { it.hasRoute<OnboardingGraph>() } ?: (startState == StartState.Onboarding)
    val layoutType =
        if (inOnboarding) {
            NavigationSuiteType.None
        } else {
            NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfoV2())
        }

    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                item(
                    selected = hierarchy?.any { it.hasRoute(destination.route::class) } == true,
                    onClick = {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                    label = { Text(stringResource(destination.label)) },
                )
            }
        },
    ) {
        DhmoNavHost(navController = navController, onboarded = startState == StartState.Main)
    }

    // Runs after the composition above, so the NavHost has already set the graph.
    LaunchedEffect(navController) {
        if (startState == StartState.Main && launchDeepLink != null) navController.handleDeepLink(launchDeepLink)
    }

    val activity = LocalActivity.current as? ComponentActivity
    val currentInOnboarding by rememberUpdatedState(inOnboarding)
    DisposableEffect(activity, navController) {
        val listener = Consumer<Intent> { intent -> if (!currentInOnboarding) navController.handleDeepLink(intent) }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }
}
