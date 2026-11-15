package casa.peterson.dhmo.feature.onboarding.navigation

import kotlinx.serialization.Serializable

@Serializable data object OnboardingGraph

/** Welcome screen. */
@Serializable data object WelcomeRoute

/** Medical disclaimer and the "I have a condition or fluid restriction" switch. */
@Serializable data object DisclaimerRoute

/** Units and day start. */
@Serializable data object UnitsRoute

/** "Calculate for me" or "I'll set my own goal" (pre-selected when the condition switch is on). */
@Serializable data object GoalModeRoute

/** Calculated path. */
@Serializable data object ProfileSetupRoute

/** Manual path. */
@Serializable data object ManualGoalRoute

/** Breakdown and disclaimer line. */
@Serializable data object GoalPreviewRoute

/** Explains reminders, then requests POST_NOTIFICATIONS (API 33+). */
@Serializable data object NotificationRationaleRoute

/** Wake and sleep times, enable switch. */
@Serializable data object ReminderSetupRoute

@Serializable data object OnboardingDoneRoute

/** Phase 5: explains rounded location to weather service; offers device location, city or skip. */
@Serializable data object WeatherRationaleRoute

/** Phase 4: optional Drive sign-in and restore. */
@Serializable data object RestoreOfferRoute
