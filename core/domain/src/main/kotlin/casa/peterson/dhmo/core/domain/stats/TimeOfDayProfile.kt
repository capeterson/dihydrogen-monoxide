package casa.peterson.dhmo.core.domain.stats

import java.time.LocalDate

private const val MINUTES_PER_DAY = 1440
private const val MINUTES_PER_HOUR = 60
private const val SLOTS = 24
private const val EVENING_START_MINUTE = 1080

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

private data class Placed(val sinceStart: Int, val volumeMl: Int)

fun timeOfDayProfile(entries: List<TimedIntake>, goals: Map<LocalDate, Int>, dayStartMinutes: Int): TimeOfDayProfile {
    val perDay: Map<LocalDate, List<Placed>> = entries
        .groupBy { it.day }
        .mapValues { (_, list) ->
            list.map { Placed(Math.floorMod(it.minuteOfDay - dayStartMinutes, MINUTES_PER_DAY), it.volumeMl) }
                .sortedBy { it.sinceStart }
        }
    val slotTotals = DoubleArray(SLOTS)
    val fractionSums = DoubleArray(SLOTS)
    for (placed in perDay.values) {
        val dayTotal = placed.sumOf { it.volumeMl }.toDouble()
        val slotVolumes = DoubleArray(SLOTS)
        placed.forEach { slotVolumes[it.sinceStart / MINUTES_PER_HOUR] += it.volumeMl.toDouble() }
        var running = 0.0
        for (k in 0 until SLOTS) {
            slotTotals[k] += slotVolumes[k]
            running += slotVolumes[k]
            if (dayTotal > 0) fractionSums[k] += running / dayTotal
        }
    }
    val divisor = maxOf(perDay.size, 1)
    val half = reachMinutes(perDay, goals) { running, goal -> 2 * running >= goal }
    val full = reachMinutes(perDay, goals) { running, goal -> running >= goal }
    return TimeOfDayProfile(
        hourlyMeanMl = slotTotals.map { it / divisor },
        cumulativeFraction = fractionSums.map { it / divisor },
        medianMinutesToHalfGoal = half.takeIf { it.isNotEmpty() }?.let(Descriptive::median),
        medianMinutesToFullGoal = full.takeIf { it.isNotEmpty() }?.let(Descriptive::median),
        daysReachingHalf = half.size,
        daysReachingFull = full.size,
        eveningShare = eveningShare(perDay, dayStartMinutes),
    )
}

private fun reachMinutes(
    perDay: Map<LocalDate, List<Placed>>,
    goals: Map<LocalDate, Int>,
    reached: (running: Int, goal: Int) -> Boolean,
): List<Double> = perDay.mapNotNull { (day, placed) ->
    val goal = goals[day]
    var running = 0
    if (goal == null) {
        null
    } else {
        placed.firstOrNull {
            running += it.volumeMl
            reached(running, goal)
        }?.sinceStart?.toDouble()
    }
}

private fun eveningShare(perDay: Map<LocalDate, List<Placed>>, dayStartMinutes: Int): Double? {
    val all = perDay.values.flatten()
    val total = all.sumOf { it.volumeMl }
    if (total <= 0) return null
    val threshold = Math.floorMod(EVENING_START_MINUTE - dayStartMinutes, MINUTES_PER_DAY)
    return all.filter { it.sinceStart >= threshold }.sumOf { it.volumeMl }.toDouble() / total
}
