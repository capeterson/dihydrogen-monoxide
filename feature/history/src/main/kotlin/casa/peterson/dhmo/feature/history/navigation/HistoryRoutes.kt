package casa.peterson.dhmo.feature.history.navigation

import kotlinx.serialization.Serializable

@Serializable data object HistoryRoute

@Serializable data class HistoryDayRoute(val epochDay: Long)
