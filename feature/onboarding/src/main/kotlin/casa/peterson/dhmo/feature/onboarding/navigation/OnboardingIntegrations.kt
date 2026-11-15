package casa.peterson.dhmo.feature.onboarding.navigation

import androidx.compose.runtime.Composable
import casa.peterson.dhmo.core.designsystem.component.ComingSoon

/**
 * Slots whose content lives in modules that `:feature:onboarding` must not depend on. `:app` replaces the
 * defaults in later phases (restore offer in Phase 4, weather rationale in Phase 5).
 */
class OnboardingIntegrations(
    val restoreOffer: @Composable (onDone: () -> Unit) -> Unit = { ComingSoon() },
    val weatherRationale: @Composable (onDone: () -> Unit) -> Unit = { ComingSoon() },
)
