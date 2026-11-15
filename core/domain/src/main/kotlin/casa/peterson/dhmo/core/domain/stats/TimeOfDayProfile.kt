@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.core.domain.stats

import java.time.LocalDate

/** `minuteOfDay` is the local clock minute (0..1439) of `instant.atOffset(entry.zoneOffset)`. */
data class TimedIntake(val day: LocalDate, val minuteOfDay: Int, val volumeMl: Int)

data class TimeOfDayProfile(
    /** 24 slots; slot k covers minutes [60k, 60k+60) since the day start. */
    val hourlyMeanMl: List<Double>,
    /** 24 values: mean over days of (volume by the end of slot k / that day's total). */
    val cumulativeFraction: List<Double>,
    /** Minutes since the day start; null when no day reached 50%. */
    val medianMinutesToHalfGoal: Double?,
    val medianMinutesToFullGoal: Double?,
    val daysReachingHalf: Int,
    val daysReachingFull: Int,
    /** Volume at or after 18:00 local as a share of all volume; null when there is no volume. */
    val eveningShare: Double?,
)

fun timeOfDayProfile(entries: List<TimedIntake>, goals: Map<LocalDate, Int>, dayStartMinutes: Int): TimeOfDayProfile = TODO("T3")
