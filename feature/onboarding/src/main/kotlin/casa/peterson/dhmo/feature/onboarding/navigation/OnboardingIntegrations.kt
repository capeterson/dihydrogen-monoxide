package casa.peterson.dhmo.feature.onboarding.navigation

import androidx.compose.runtime.Composable

/**
 * Slots whose content lives in modules that `:feature:onboarding` must not depend on. `:app` replaces the
 * defaults in later phases (the Drive restore offer).
 */
class OnboardingIntegrations(
    /** Offered from the welcome screen on a first run. Null when this build cannot restore from Drive. */
    val restoreOffer: (@Composable (onSkip: () -> Unit, onRestored: () -> Unit) -> Unit)? = null,
)
