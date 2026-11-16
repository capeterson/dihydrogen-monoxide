package casa.peterson.dhmo.feature.insights

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class InsightsRange { DAY, WEEK, MONTH, YEAR, CUSTOM }

/** The visible range. [to] may lie in the future (the rest of this week); data is loaded up to today only. */
data class RangeSelection(val range: InsightsRange, val from: LocalDate, val to: LocalDate, val compare: Boolean = false) {
    init {
        require(!to.isBefore(from)) { "Range ends before it starts" }
    }

    val lengthDays: Long get() = ChronoUnit.DAYS.between(from, to) + 1

    /** The equal-length period immediately before this one. */
    fun previousPeriod(): Pair<LocalDate, LocalDate> = from.minusDays(lengthDays) to from.minusDays(1)

    fun shifted(forward: Boolean): RangeSelection {
        val sign = if (forward) 1L else -1L
        return when (range) {
            InsightsRange.DAY -> forRange(range, from.plusDays(sign))
            InsightsRange.WEEK -> forRange(range, from.plusWeeks(sign))
            InsightsRange.MONTH -> forRange(range, from.plusMonths(sign))
            InsightsRange.YEAR -> forRange(range, from.plusYears(sign))
            InsightsRange.CUSTOM -> copy(from = from.plusDays(sign * lengthDays), to = to.plusDays(sign * lengthDays))
        }
    }

    /** There is nothing to show after today. */
    fun hasNext(today: LocalDate): Boolean = !shifted(forward = true).from.isAfter(today)

    companion object {
        /** The calendar day, ISO week (Monday to Sunday), month or year containing [anchor]. */
        fun forRange(range: InsightsRange, anchor: LocalDate): RangeSelection = when (range) {
            InsightsRange.DAY -> RangeSelection(range, anchor, anchor)

            InsightsRange.WEEK -> {
                val monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                RangeSelection(range, monday, monday.plusDays(DAYS_PER_WEEK - 1))
            }

            InsightsRange.MONTH -> RangeSelection(range, anchor.withDayOfMonth(1), anchor.with(TemporalAdjusters.lastDayOfMonth()))

            InsightsRange.YEAR -> RangeSelection(range, anchor.withDayOfYear(1), anchor.with(TemporalAdjusters.lastDayOfYear()))

            InsightsRange.CUSTOM -> RangeSelection(range, anchor.minusDays(DEFAULT_CUSTOM_DAYS - 1), anchor)
        }

        /** Custom ranges are limited to one year (the longest range §9.6 sizes the trend for). */
        const val MAX_CUSTOM_DAYS = 366L
        private const val DEFAULT_CUSTOM_DAYS = 30L
        private const val DAYS_PER_WEEK = 7L
    }
}
