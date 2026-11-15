package casa.peterson.dhmo.feature.profile.navigation

import kotlinx.serialization.Serializable

/** Goal summary, breakdown, overrides. */
@Serializable data object ProfileGoalRoute

@Serializable data object EditProfileRoute

/** Fixed goal, optional per weekday. */
@Serializable data object GlobalOverrideRoute

@Serializable data class DayOverrideRoute(val epochDay: Long)

/** Toggles and pinned inputs. */
@Serializable data object AdjustmentsRoute

/** Citations and disclaimer. */
@Serializable data object SourcesRoute
