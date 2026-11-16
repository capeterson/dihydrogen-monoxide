package casa.peterson.dhmo.feature.insights

import casa.peterson.dhmo.core.data.repository.GoalRepository
import casa.peterson.dhmo.core.domain.stats.DayStat
import casa.peterson.dhmo.core.domain.stats.InsightInput
import casa.peterson.dhmo.core.domain.stats.buildInsights
import casa.peterson.dhmo.core.model.ClampFlag
import casa.peterson.dhmo.core.model.DailyGoal
import casa.peterson.dhmo.core.model.GoalBreakdown
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.model.IntakeEntry
import casa.peterson.dhmo.core.model.Source
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.feature.insights.domain.toTimedIntake
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * TECHNICAL_PLAN §9.14 dataset A: Monday 2026-03-02 to Sunday 2026-03-15, goal 2,000 ml every day, no entries on
 * Wednesday 04. Each logged day's total is split over 08:00, 13:00 and 19:00 UTC (the last part takes the remainder).
 */
internal object DatasetA {
    val first: LocalDate = LocalDate.of(2026, 3, 2)
    val last: LocalDate = LocalDate.of(2026, 3, 15)

    /** The Monday after the dataset, so every dataset day is complete. */
    val today: LocalDate = LocalDate.of(2026, 3, 16)
    val totals = listOf(2100, 1800, 0, 2500, 1950, 2000, 1200, 2200, 2300, 1600, 2000, 2050, 900, 2400)
    const val GOAL_ML = 2000

    val days: List<DayStat> = totals.mapIndexed { i, total ->
        DayStat(first.plusDays(i.toLong()), total, GOAL_ML, if (total == 0) 0 else 3)
    }

    val entries: List<IntakeEntry> = totals.flatMapIndexed { i, total ->
        if (total == 0) return@flatMapIndexed emptyList()
        val day = first.plusDays(i.toLong())
        val part = total / 3
        listOf(8 to part, 13 to part, 19 to total - 2 * part).map { (hour, volume) -> entry(day, hour, volume) }
    }

    fun entry(day: LocalDate, hour: Int, volumeMl: Int): IntakeEntry {
        val instant = day.atTime(LocalTime.of(hour, 0)).toInstant(ZoneOffset.UTC)
        return IntakeEntry(
            id = UUID.nameUUIDFromBytes("$day-$hour".toByteArray()),
            instant = instant,
            zoneOffset = ZoneOffset.UTC,
            hydrationDay = day,
            volumeMl = volumeMl,
            source = Source.MANUAL,
            createdAt = instant,
            updatedAt = instant,
        )
    }

    fun goal(day: LocalDate, targetMl: Int = GOAL_ML) = DailyGoal(
        date = day,
        targetMl = targetMl,
        calculatedMl = null,
        mode = GoalMode.GLOBAL_OVERRIDE,
        breakdown = GoalBreakdown(1, 0, emptyList(), targetMl, targetMl, targetMl, ClampFlag.NONE),
        dayStartMinutes = 0,
        policyVersion = 1,
        updatedAt = Instant.EPOCH,
    )

    suspend fun saveGoals(goals: GoalRepository, from: LocalDate = first, to: LocalDate = today) {
        var day = from
        while (!day.isAfter(to)) {
            goals.saveSnapshot(goal(day))
            day = day.plusDays(1)
        }
    }

    /** The Ready state for [selection] over dataset A, computed exactly as the view model does. */
    fun ready(selection: RangeSelection, settings: AppSettings = AppSettings()): InsightsUiState.Ready {
        val chartTo = minOf(selection.to, today)
        val statsFrom = minOf(first, selection.from.minusDays(ROLLING_LONG - 1L))
        val history = buildList {
            var day = statsFrom
            while (!day.isAfter(chartTo)) {
                add(days.firstOrNull { it.date == day } ?: DayStat(day, 0, if (day == today) GOAL_ML else null, 0))
                day = day.plusDays(1)
            }
        }
        val (previousFrom, previousTo) = selection.previousPeriod()
        val inputs = InsightsInputs(
            selection = selection,
            today = today,
            settings = settings,
            zone = ZoneOffset.UTC,
            firstDataDay = first,
            statsFrom = statsFrom,
            history = history,
            entries = entries.filter { it.hydrationDay in selection.from..chartTo },
            previous = if (selection.compare) history.filter { it.date in previousFrom..previousTo } else emptyList(),
        )
        val insights = buildInsights(
            InsightInput(
                today = today,
                days = days,
                entries = entries.map { it.toTimedIntake() },
                temperature = emptyList(),
                activeMinutes = emptyList(),
                dayStartMinutes = 0,
                includeNoLogDays = false,
                weeklyFreeze = false,
            ),
        )
        return computeInsights(inputs, insights)
    }
}
