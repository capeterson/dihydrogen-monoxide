package casa.peterson.dhmo.feature.history.navigation

import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable data object HistoryRoute

/** Also the target of `dhmo://history/day/{epochDay}`, so [epochDay] may be any `Long`. Read the day through [date]. */
@Serializable
data class HistoryDayRoute(val epochDay: Long) {
    /** The day, or null when [epochDay] is outside the range of [LocalDate] (where `ofEpochDay` would throw). */
    val date: LocalDate?
        get() = if (epochDay in MIN_EPOCH_DAY..MAX_EPOCH_DAY) LocalDate.ofEpochDay(epochDay) else null

    private companion object {
        val MIN_EPOCH_DAY = LocalDate.MIN.toEpochDay()
        val MAX_EPOCH_DAY = LocalDate.MAX.toEpochDay()
    }
}
