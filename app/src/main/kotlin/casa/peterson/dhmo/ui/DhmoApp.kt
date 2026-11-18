package casa.peterson.dhmo.ui

import android.content.Intent
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuite
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import casa.peterson.dhmo.StartState
import casa.peterson.dhmo.core.designsystem.theme.LocalDhmoExtraColors
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
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val layoutType =
        if (inOnboarding) {
            NavigationSuiteType.None
        } else if (landscape) {
            NavigationSuiteType.NavigationRail
        } else {
            NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfoV2())
        }

    // A sub-screen (Appearance, the goal) belongs to the tab it was opened from, so that tab stays lit.
    var activeTab by rememberSaveable { mutableStateOf(TopLevelDestination.Today) }
    TopLevelDestination.entries.firstOrNull { tab -> hierarchy?.any { it.hasRoute(tab.route::class) } == true }?.let { activeTab = it }
    val colors = MaterialTheme.colorScheme
    val extras = LocalDhmoExtraColors.current
    val onGlow = if (extras.isNight) colors.background else colors.onSurface
    val itemColors =
        NavigationSuiteDefaults.itemColors(
            navigationBarItemColors =
                NavigationBarItemDefaults.colors(
                    selectedIconColor = onGlow,
                    selectedTextColor = colors.onSurface,
                    indicatorColor = extras.glow,
                    unselectedIconColor = colors.onSurfaceVariant,
                    unselectedTextColor = colors.onSurfaceVariant,
                ),
            navigationRailItemColors =
                NavigationRailItemDefaults.colors(
                    selectedIconColor = onGlow,
                    selectedTextColor = colors.onSurface,
                    indicatorColor = extras.glow,
                    unselectedIconColor = colors.onSurfaceVariant,
                    unselectedTextColor = colors.onSurfaceVariant,
                ),
        )
    // The bar is the design's wooden shelf: the paper-dark surface with a lit 1 dp edge along the top.
    NavigationSuiteScaffoldLayout(
        layoutType = layoutType,
        navigationSuite = {
            val highlight = extras.shelfHighlight
            Box(
                Modifier.drawBehind {
                    if (layoutType ==
                        NavigationSuiteType.NavigationBar
                    ) {
                        drawLine(highlight, Offset(0f, HAIRLINE), Offset(size.width, HAIRLINE), 1.dp.toPx())
                    }
                },
            ) {
                NavigationSuite(
                    layoutType = layoutType,
                    colors =
                        NavigationSuiteDefaults.colors(
                            navigationBarContainerColor = colors.surfaceVariant,
                            navigationRailContainerColor = colors.surfaceVariant,
                        ),
                ) {
                    TopLevelDestination.entries.forEach { destination ->
                        item(
                            selected = activeTab == destination,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                            label = { Text(stringResource(destination.label)) },
                            colors = itemColors,
                        )
                    }
                }
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

private const val HAIRLINE = 0.5f
