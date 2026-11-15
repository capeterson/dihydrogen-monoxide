package casa.peterson.dhmo.feature.insights.navigation

import kotlinx.serialization.Serializable

/** `range` is one of DAY, WEEK, MONTH, YEAR, CUSTOM. */
@Serializable data class InsightsRoute(val range: String = "WEEK", val anchorEpochDay: Long? = null)

@Serializable data class CustomRangeRoute(val fromEpochDay: Long, val toEpochDay: Long, val compare: Boolean = false)

/** `driver` is one of TEMPERATURE, ACTIVE_MINUTES, STEPS, WEIGHT. */
@Serializable data class CorrelationRoute(val driver: String)
