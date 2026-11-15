package casa.peterson.dhmo.feature.today.navigation

import kotlinx.serialization.Serializable

@Serializable data object TodayRoute

/** Bottom sheet destination; also opened from History. */
@Serializable data class EditEntryRoute(val entryId: String)

/** Manage quick-add presets. */
@Serializable data object ContainersRoute

@Serializable data object BatteryHelpRoute
