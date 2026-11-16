package casa.peterson.dhmo.feature.insights

import casa.peterson.dhmo.core.domain.stats.Attainment
import casa.peterson.dhmo.core.domain.stats.DayOfWeekPattern
import casa.peterson.dhmo.core.domain.stats.DayResult
import casa.peterson.dhmo.core.domain.stats.DayStat
import casa.peterson.dhmo.core.domain.stats.Insight
import casa.peterson.dhmo.core.domain.stats.Streaks
import casa.peterson.dhmo.core.domain.stats.Summary
import casa.peterson.dhmo.core.domain.stats.TimeOfDayProfile
import casa.peterson.dhmo.core.domain.stats.Trend
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import java.time.LocalDate

/** One hour slot of the Day view, counted from the day start: what was drunk in it and the running total at its end. */
data class HourBar(val slot: Int, val volumeMl: Int, val cumulativeMl: Int)

/** TECHNICAL_PLAN §13.7. */
sealed interface InsightsUiState {
    data object Loading : InsightsUiState

    /** No entries yet. */
    data object Empty : InsightsUiState

    data class Ready(
        val selection: RangeSelection,
        val today: LocalDate,
        /** One element per day from `from` to `min(to, today)`, today included. */
        val days: List<DayStat>,
        val results: Map<LocalDate, DayResult>,
        /** Over complete days only (today excluded, §9.1). */
        val summary: Summary?,
        val attainment: Attainment,
        val goalStreak: Streaks,
        val loggingStreak: Streaks,
        /** Aligned with [days]. */
        val rolling7: List<Double?>,
        val rolling30: List<Double?>,
        val trend: Trend,
        val consistency: Int?,
        val dayOfWeek: DayOfWeekPattern,
        val timeOfDay: TimeOfDayProfile?,
        /** DAY only: 24 slots from the day start. */
        val hourly: List<HourBar>?,
        /** DAY only: expected running total (ml) at the end of each slot; null without a goal. */
        val expectedLine: List<Float>?,
        /** CUSTOM with "compare": the summary of the previous equal-length period. */
        val comparison: Summary?,
        val insights: List<Insight>,
        val units: VolumeUnit,
        val dayStartMinutes: Int,
        val firstDataDay: LocalDate?,
    ) : InsightsUiState {
        val hasPrevious: Boolean get() = firstDataDay != null && selection.from.isAfter(firstDataDay)
        val hasNext: Boolean get() = selection.hasNext(today)

        /** The goal line: the latest goal in the range. */
        val goalMl: Int? get() = days.lastOrNull { it.goalMl != null }?.goalMl
    }
}
