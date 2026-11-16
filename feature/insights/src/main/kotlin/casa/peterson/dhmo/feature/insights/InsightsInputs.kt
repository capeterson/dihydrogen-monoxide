package casa.peterson.dhmo.feature.insights

import casa.peterson.dhmo.core.domain.pace.PaceCurve
import casa.peterson.dhmo.core.domain.pace.expectedMl
import casa.peterson.dhmo.core.domain.pace.wakeWindow
import casa.peterson.dhmo.core.domain.stats.DayStat
import casa.peterson.dhmo.core.domain.stats.Insight
import casa.peterson.dhmo.core.domain.stats.attainment
import casa.peterson.dhmo.core.domain.stats.consistencyScore
import casa.peterson.dhmo.core.domain.stats.dayOfWeekPattern
import casa.peterson.dhmo.core.domain.stats.dayResult
import casa.peterson.dhmo.core.domain.stats.goalStreak
import casa.peterson.dhmo.core.domain.stats.loggingStreak
import casa.peterson.dhmo.core.domain.stats.rollingMean
import casa.peterson.dhmo.core.domain.stats.summary
import casa.peterson.dhmo.core.domain.stats.timeOfDayProfile
import casa.peterson.dhmo.core.domain.stats.trend
import casa.peterson.dhmo.core.model.IntakeEntry
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.feature.insights.domain.toTimedIntake
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Everything one Insights computation needs; also the cache key, so equal inputs reuse the last result. */
internal data class InsightsInputs(
    val selection: RangeSelection,
    val today: LocalDate,
    val settings: AppSettings,
    val zone: ZoneId,
    val firstDataDay: LocalDate?,
    /** Contiguous from [statsFrom] to `min(selection.to, today)`. */
    val statsFrom: LocalDate,
    val history: List<DayStat>,
    /** Entries of the visible range up to today. */
    val entries: List<IntakeEntry>,
    /** The previous equal-length period, when comparing. */
    val previous: List<DayStat>,
)

/** Pure: turns [InsightsInputs] into the Ready state. */
internal fun computeInsights(inputs: InsightsInputs, insights: List<Insight>): InsightsUiState.Ready {
    val selection = inputs.selection
    val includeNoLog = inputs.settings.stats.includeNoLogDays
    val freeze = inputs.settings.stats.weeklyStreakFreeze
    val chartTo = minOf(selection.to, inputs.today)
    val days = inputs.history.filter { it.date in selection.from..chartTo }
    // Statistics use complete days only: today's total is still growing (§9.1).
    val complete = days.filter { it.date.isBefore(inputs.today) }
    val streakDays = inputs.history.filter { day -> inputs.firstDataDay?.let { !day.date.isBefore(it) } ?: false }
    val todayInProgress = !chartTo.isBefore(inputs.today)
    val offset = inputs.history.indexOfFirst { it.date == selection.from }.coerceAtLeast(0)
    val goals = complete.mapNotNull { day -> day.goalMl?.let { day.date to it } }.toMap()
    val completeEntries = inputs.entries.filter { it.hydrationDay.isBefore(inputs.today) }
    return InsightsUiState.Ready(
        selection = selection,
        today = inputs.today,
        days = days,
        results = days.associate { it.date to dayResult(it) },
        summary = summary(complete, includeNoLog),
        attainment = attainment(complete, includeNoLog),
        goalStreak = goalStreak(streakDays, freeze, todayInProgress),
        loggingStreak = loggingStreak(streakDays, freeze, todayInProgress),
        rolling7 = rollingMean(inputs.history, ROLLING_SHORT, includeNoLog).drop(offset).take(days.size),
        rolling30 = rollingMean(inputs.history, ROLLING_LONG, includeNoLog).drop(offset).take(days.size),
        trend = trend(complete, includeNoLog),
        consistency = consistencyScore(complete, includeNoLog),
        dayOfWeek = dayOfWeekPattern(complete, includeNoLog),
        timeOfDay = completeEntries.takeIf { it.isNotEmpty() }?.let { entries ->
            timeOfDayProfile(entries.map(IntakeEntry::toTimedIntake), goals, inputs.settings.dayStartMinutes)
        },
        hourly = if (selection.range == InsightsRange.DAY) hourlyBars(inputs.entries, inputs.settings.dayStartMinutes) else null,
        expectedLine = if (selection.range == InsightsRange.DAY) expectedLine(selection.from, days.firstOrNull()?.goalMl, inputs) else null,
        comparison = if (selection.compare) summary(inputs.previous, includeNoLog) else null,
        insights = insights,
        units = inputs.settings.units,
        dayStartMinutes = inputs.settings.dayStartMinutes,
        firstDataDay = inputs.firstDataDay,
    )
}

/** 24 slots counted from the day start, each with its volume and the running total at its end. */
internal fun hourlyBars(entries: List<IntakeEntry>, dayStartMinutes: Int): List<HourBar> {
    val perSlot = IntArray(HOURS_PER_DAY)
    entries.map { it.toTimedIntake() }.forEach { intake ->
        val sinceStart = Math.floorMod(intake.minuteOfDay - dayStartMinutes, MINUTES_PER_DAY)
        perSlot[sinceStart / MINUTES_PER_HOUR] += intake.volumeMl
    }
    var running = 0
    return perSlot.mapIndexed { slot, volume ->
        running += volume
        HourBar(slot, volume, running)
    }
}

/** The expected running total at the end of each slot from the pace curve over the wake window (§8.2). */
internal fun expectedLine(day: LocalDate, goalMl: Int?, inputs: InsightsInputs): List<Float>? {
    if (goalMl == null || goalMl <= 0) return null
    val reminders = inputs.settings.reminders
    val dayStart = minutesToTime(inputs.settings.dayStartMinutes)
    val window = wakeWindow(day, minutesToTime(reminders.wakeMinutes), minutesToTime(reminders.sleepMinutes), dayStart, inputs.zone)
    val curve = PaceCurve.from(reminders.curve)
    val start = day.atTime(dayStart).atZone(inputs.zone).toInstant()
    return (1..HOURS_PER_DAY).map { hour ->
        val at = start.plus(Duration.ofHours(hour.toLong()))
        // The curve clamps to [0, 1], so slots before wake expect 0 and slots after sleep the full goal.
        expectedMl(goalMl, at, window, curve).toFloat()
    }
}

private fun minutesToTime(minutes: Int): LocalTime = LocalTime.ofSecondOfDay(minutes.coerceIn(0, MINUTES_PER_DAY - 1) * SECONDS_PER_MINUTE)

internal const val ROLLING_SHORT = 7
internal const val ROLLING_LONG = 30
private const val HOURS_PER_DAY = 24
private const val SECONDS_PER_MINUTE = 60L
