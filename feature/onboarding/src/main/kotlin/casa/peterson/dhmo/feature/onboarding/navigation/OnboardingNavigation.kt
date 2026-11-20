package casa.peterson.dhmo.feature.onboarding.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import casa.peterson.dhmo.feature.onboarding.GoalChoice
import casa.peterson.dhmo.feature.onboarding.OnboardingPage
import casa.peterson.dhmo.feature.onboarding.OnboardingPageContent
import casa.peterson.dhmo.feature.onboarding.OnboardingViewModel
import casa.peterson.dhmo.feature.onboarding.WeatherRationaleScreen

internal val LocalOnboardingViewModel = staticCompositionLocalOf<OnboardingViewModel?> { null }

fun NavGraphBuilder.onboardingGraph(
    navController: NavController,
    onFinished: () -> Unit,
    integrations: OnboardingIntegrations = OnboardingIntegrations(),
) {
    navigation<OnboardingGraph>(startDestination = WelcomeRoute) {
        composable<WelcomeRoute> { entry ->
            Page(navController, entry, OnboardingPage.WELCOME, onFinished, integrations.restoreOffer != null)
        }
        composable<DisclaimerRoute> { entry -> Page(navController, entry, OnboardingPage.DISCLAIMER, onFinished) }
        composable<UnitsRoute> { entry -> Page(navController, entry, OnboardingPage.UNITS, onFinished) }
        composable<GoalModeRoute> { entry -> Page(navController, entry, OnboardingPage.GOAL_MODE, onFinished) }
        composable<ProfileSetupRoute> { entry -> Page(navController, entry, OnboardingPage.PROFILE, onFinished) }
        composable<ManualGoalRoute> { entry -> Page(navController, entry, OnboardingPage.MANUAL, onFinished) }
        composable<GoalPreviewRoute> { entry -> Page(navController, entry, OnboardingPage.PREVIEW, onFinished) }
        composable<NotificationRationaleRoute> { entry -> Page(navController, entry, OnboardingPage.NOTIFICATION, onFinished) }
        composable<ReminderSetupRoute> { entry -> Page(navController, entry, OnboardingPage.REMINDERS, onFinished) }
        composable<RestoreOfferRoute> {
            integrations.restoreOffer?.invoke(
                { navController.navigate(DisclaimerRoute) },
                onFinished,
            )
        }
        composable<WeatherRationaleRoute> { WeatherRationaleScreen(onDone = { navController.navigate(OnboardingDoneRoute) }) }
        composable<OnboardingDoneRoute> { entry -> Page(navController, entry, OnboardingPage.DONE, onFinished) }
    }
}

@Composable
private fun Page(
    nav: NavController,
    entry: NavBackStackEntry,
    page: OnboardingPage,
    onFinished: () -> Unit,
    offerRestore: Boolean = false,
) {
    val parent = remember(entry) { nav.getBackStackEntry<OnboardingGraph>() }
    val viewModel: OnboardingViewModel = LocalOnboardingViewModel.current ?: hiltViewModel(parent)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.change { it.copy(notificationsGranted = granted) }
        viewModel.persistStep(OnboardingPage.REMINDERS.name)
        nav.navigate(ReminderSetupRoute)
    }
    LaunchedEffect(page, state.loading, state.recoverGoal) {
        if (!state.loading && state.recoverGoal && page != OnboardingPage.GOAL_MODE) {
            viewModel.persistStep(OnboardingPage.GOAL_MODE.name)
            nav.navigate(GoalModeRoute) { popUpTo<OnboardingGraph> { inclusive = false } }
            return@LaunchedEffect
        }
        if (!state.loading && page == OnboardingPage.REMINDERS) {
            val granted = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            viewModel.change { it.copy(notificationsGranted = granted) }
        }
        if (!state.loading && page != OnboardingPage.WELCOME) viewModel.persistStep(page.name)
        if (page == OnboardingPage.WELCOME && !state.loading) {
            val saved = viewModel.takeResumeStep()
            if (saved != null && saved != OnboardingPage.WELCOME.name) {
                when (saved) {
                    OnboardingPage.DISCLAIMER.name -> nav.navigate(DisclaimerRoute)
                    OnboardingPage.UNITS.name -> nav.navigate(UnitsRoute)
                    OnboardingPage.GOAL_MODE.name -> nav.navigate(GoalModeRoute)
                    OnboardingPage.PROFILE.name -> nav.navigate(ProfileSetupRoute)
                    OnboardingPage.MANUAL.name -> nav.navigate(ManualGoalRoute)
                    OnboardingPage.PREVIEW.name -> nav.navigate(GoalPreviewRoute)
                    OnboardingPage.NOTIFICATION.name -> nav.navigate(NotificationRationaleRoute)
                    OnboardingPage.REMINDERS.name -> nav.navigate(ReminderSetupRoute)
                    OnboardingPage.DONE.name -> nav.navigate(OnboardingDoneRoute)
                }
            }
        }
    }
    fun advance(nextPage: OnboardingPage) {
        viewModel.persistStep(nextPage.name)
        when (nextPage) {
            OnboardingPage.DISCLAIMER -> nav.navigate(DisclaimerRoute)
            OnboardingPage.UNITS -> nav.navigate(UnitsRoute)
            OnboardingPage.GOAL_MODE -> nav.navigate(GoalModeRoute)
            OnboardingPage.PROFILE -> nav.navigate(ProfileSetupRoute)
            OnboardingPage.MANUAL -> nav.navigate(ManualGoalRoute)
            OnboardingPage.PREVIEW -> nav.navigate(GoalPreviewRoute)
            OnboardingPage.NOTIFICATION -> nav.navigate(NotificationRationaleRoute)
            OnboardingPage.REMINDERS -> nav.navigate(ReminderSetupRoute)
            OnboardingPage.DONE -> nav.navigate(OnboardingDoneRoute)
            OnboardingPage.WELCOME -> nav.navigate(WelcomeRoute)
        }
    }
    OnboardingPageContent(
        page = page,
        state = state,
        preview = if (page == OnboardingPage.PREVIEW) viewModel.preview() else null,
        change = viewModel::change,
        next = {
            when (page) {
                OnboardingPage.WELCOME -> if (state.returning) {
                    advance(OnboardingPage.NOTIFICATION)
                } else if (offerRestore) {
                    nav.navigate(RestoreOfferRoute)
                } else {
                    advance(OnboardingPage.DISCLAIMER)
                }

                OnboardingPage.DISCLAIMER -> {
                    viewModel.acceptDisclaimer()
                    advance(OnboardingPage.UNITS)
                }

                OnboardingPage.UNITS -> advance(OnboardingPage.GOAL_MODE)

                OnboardingPage.GOAL_MODE -> if (state.choice ==
                    GoalChoice.CALCULATED
                ) {
                    advance(OnboardingPage.PROFILE)
                } else {
                    advance(OnboardingPage.MANUAL)
                }

                OnboardingPage.PROFILE -> if (viewModel.validProfile()) advance(OnboardingPage.PREVIEW)

                OnboardingPage.MANUAL -> if (viewModel.validManualGoal()) advance(OnboardingPage.PREVIEW)

                OnboardingPage.PREVIEW -> advance(OnboardingPage.NOTIFICATION)

                OnboardingPage.NOTIFICATION -> Unit

                OnboardingPage.REMINDERS -> nav.navigate(WeatherRationaleRoute)

                OnboardingPage.DONE -> viewModel.finish(onFinished)
            }
        },
        back = { nav.popBackStack() },
        allowNotifications = {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.change { it.copy(notificationsGranted = true) }
                advance(OnboardingPage.REMINDERS)
            }
        },
        skipNotifications = {
            viewModel.change { it.copy(notificationsGranted = false, remindersEnabled = false) }
            advance(OnboardingPage.REMINDERS)
        },
        skipSetup = { viewModel.skip(onFinished) },
    )
}
