package casa.peterson.dhmo.feature.profile.navigation

import java.time.LocalDate
import kotlinx.serialization.Serializable

/** Goal summary, breakdown, overrides. */
@Serializable data object ProfileGoalRoute

@Serializable data object EditProfileRoute

/** Fixed goal, optional per weekday. */
@Serializable data object GlobalOverrideRoute

@Serializable
data class DayOverrideRoute(val epochDay: Long) {
    /** The day, or null when [epochDay] is outside the range of [LocalDate] (where `ofEpochDay` would throw). */
    val date: LocalDate?
        get() = if (epochDay in MIN_EPOCH_DAY..MAX_EPOCH_DAY) LocalDate.ofEpochDay(epochDay) else null

    private companion object {
        val MIN_EPOCH_DAY = LocalDate.MIN.toEpochDay()
        val MAX_EPOCH_DAY = LocalDate.MAX.toEpochDay()
    }
}

/** Toggles and pinned inputs. */
@Serializable data object AdjustmentsRoute

/** Citations and disclaimer. */
@Serializable data object SourcesRoute
