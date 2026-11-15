package casa.peterson.dhmo.core.domain.stats

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class InsightsTest {
    private val today: LocalDate = LocalDate.of(2026, 3, 16)

    private fun input(
        days: List<DayStat> = emptyList(),
        entries: List<TimedIntake> = emptyList(),
        temperature: List<Pair<Double, Double>> = emptyList(),
        activeMinutes: List<Pair<Double, Double>> = emptyList(),
        includeNoLogDays: Boolean = false,
        weeklyFreeze: Boolean = false,
    ) = InsightInput(today, days, entries, temperature, activeMinutes, 0, includeNoLogDays, weeklyFreeze)

    /** [totals] ending yesterday (Sunday 2026-03-15). */
    private fun daysEndingYesterday(totals: List<Int>): List<DayStat> = daysOf(totals, start = today.minusDays(totals.size.toLong()))

    private fun find(kind: InsightKind, input: InsightInput): Insight? = buildInsights(input).firstOrNull { it.kind == kind }

    private fun entriesEveryDay(vararg slots: Pair<Int, Int>, dayCount: Int = 10): List<TimedIntake> =
        (1..dayCount).flatMap { back -> slots.map { (minute, ml) -> TimedIntake(today.minusDays(back.toLong()), minute, ml) } }

    // ANOMALY

    @Test
    fun `anomaly fires for a recent outlier`() {
        val insight = requireNotNull(find(InsightKind.ANOMALY, input(days = daysEndingYesterday(DATASET_F))))
        assertThat(insight.priority).isEqualTo(90)
        assertThat(insight.chart).isEqualTo(InsightChart.HISTORY_DAY)
        assertThat(insight.params).containsExactly("date", "2026-03-14", "totalMl", "500")
    }

    @Test
    fun `anomaly does not fire when the outlier is older than 7 days`() {
        val days = daysOf(DATASET_F, start = today.minusDays(30))
        assertThat(find(InsightKind.ANOMALY, input(days = days))).isNull()
    }

    // STREAK

    @Test
    fun `streak fires at three days with priority 80`() {
        val insight =
            requireNotNull(find(InsightKind.STREAK, input(days = daysEndingYesterday(listOf(1000, 2000, 2000, 2000, 2000, 2000)))))
        assertThat(insight.priority).isEqualTo(80)
        assertThat(insight.params).containsExactly("days", "5", "isLongest", "true")
    }

    @Test
    fun `streak that is the longest and at least seven days has priority 85`() {
        val insight = requireNotNull(find(InsightKind.STREAK, input(days = daysEndingYesterday(List(8) { 2000 }))))
        assertThat(insight.priority).isEqualTo(85)
    }

    @Test
    fun `streak of two days does not fire`() {
        assertThat(find(InsightKind.STREAK, input(days = daysEndingYesterday(listOf(2000, 2000))))).isNull()
    }

    // GOAL_HITS_LAST_7

    @Test
    fun `goal hits fires with at least five logged days`() {
        val insight =
            requireNotNull(
                find(InsightKind.GOAL_HITS_LAST_7, input(days = daysEndingYesterday(listOf(2000, 2000, 1000, 2000, 2000, 2000, 2000)))),
            )
        assertThat(insight.priority).isEqualTo(70)
        assertThat(insight.params).containsExactly("met", "6", "of", "7")
    }

    @Test
    fun `goal hits does not fire with four logged days`() {
        assertThat(find(InsightKind.GOAL_HITS_LAST_7, input(days = daysEndingYesterday(listOf(0, 0, 0, 2000, 2000, 2000, 2000))))).isNull()
    }

    // TREND

    @Test
    fun `trend fires for a steady rise`() {
        val insight = requireNotNull(find(InsightKind.TREND, input(days = daysEndingYesterday(List(56) { 1500 + 10 * it }))))
        assertThat(insight.priority).isEqualTo(65)
        assertThat(insight.chart).isEqualTo(InsightChart.TREND)
        assertThat(insight.params).containsExactly("direction", "UP", "changeMl", "550", "weeks", "8")
    }

    @Test
    fun `trend does not fire for a flat series`() {
        assertThat(find(InsightKind.TREND, input(days = daysEndingYesterday(List(56) { 2000 })))).isNull()
    }

    // WEEKEND_VS_WEEKDAY

    @Test
    fun `weekend rule fires when weekends are 25 percent lower`() {
        val totals = (0 until 28).map { if ((it % 7) >= 5) 1500 else 2000 }
        val insight = requireNotNull(find(InsightKind.WEEKEND_VS_WEEKDAY, input(days = daysEndingYesterday(totals))))
        assertThat(insight.priority).isEqualTo(60)
        assertThat(insight.params).containsExactly("percent", "25", "lower", "true")
    }

    @Test
    fun `weekend rule does not fire for similar averages or too few days`() {
        assertThat(find(InsightKind.WEEKEND_VS_WEEKDAY, input(days = daysEndingYesterday(List(28) { 2000 })))).isNull()
        assertThat(find(InsightKind.WEEKEND_VS_WEEKDAY, input(days = daysEndingYesterday(List(7) { if (it % 7 >= 5) 1000 else 2000 }))))
            .isNull()
    }

    @Test
    fun `dataset A weekend gap is 21 percent`() {
        // Dataset A ends on 2026-03-15; it has 9 included weekdays and 4 weekend days.
        val insight = requireNotNull(find(InsightKind.WEEKEND_VS_WEEKDAY, input(days = datasetA(), includeNoLogDays = false)))
        assertThat(insight.params).containsExactly("percent", "21", "lower", "true")
    }

    // BIGGEST_GAP_BLOCK

    @Test
    fun `gap block fires when one block is nearly empty`() {
        val entries = entriesEveryDay(9 * 60 to 500, 12 * 60 to 500, 18 * 60 to 500)
        val insight = requireNotNull(find(InsightKind.BIGGEST_GAP_BLOCK, input(entries = entries)))
        assertThat(insight.priority).isEqualTo(55)
        assertThat(insight.params).containsExactly("block", "14-17")
    }

    @Test
    fun `gap block does not fire for even blocks or fewer than seven days`() {
        val even = entriesEveryDay(9 * 60 to 500, 12 * 60 to 500, 15 * 60 to 500, 18 * 60 to 500)
        assertThat(find(InsightKind.BIGGEST_GAP_BLOCK, input(entries = even))).isNull()
        val short = entriesEveryDay(9 * 60 to 500, 12 * 60 to 500, 18 * 60 to 500, dayCount = 6)
        assertThat(find(InsightKind.BIGGEST_GAP_BLOCK, input(entries = short))).isNull()
    }

    // EVENING_SHARE

    @Test
    fun `evening share fires at 40 percent or more`() {
        val insight = requireNotNull(find(InsightKind.EVENING_SHARE, input(entries = entriesEveryDay(10 * 60 to 100, 19 * 60 to 200))))
        assertThat(insight.priority).isEqualTo(50)
        assertThat(insight.params).containsExactly("percent", "67")
    }

    @Test
    fun `evening share does not fire for a daytime drinker`() {
        assertThat(find(InsightKind.EVENING_SHARE, input(entries = entriesEveryDay(10 * 60 to 300, 19 * 60 to 100)))).isNull()
    }

    // WEAKEST_DAY

    @Test
    fun `weakest day fires when one weekday is well below average`() {
        val totals = List(56) { if ((it % 7) == 6) 1500 else 2000 }
        val insight = requireNotNull(find(InsightKind.WEAKEST_DAY, input(days = daysEndingYesterday(totals))))
        assertThat(insight.priority).isEqualTo(45)
        assertThat(insight.params).containsExactly("day", "SUNDAY", "percentBelow", "22")
    }

    @Test
    fun `weakest day does not fire for a flat week or too few samples`() {
        assertThat(find(InsightKind.WEAKEST_DAY, input(days = daysEndingYesterday(List(56) { 2000 })))).isNull()
        val short = List(14) { if ((it % 7) == 6) 1000 else 2000 }
        assertThat(find(InsightKind.WEAKEST_DAY, input(days = daysEndingYesterday(short)))).isNull()
    }

    // TEMPERATURE_CORRELATION and ACTIVITY_CORRELATION

    @Test
    fun `temperature correlation fires and activity stays silent`() {
        val result = buildInsights(input(temperature = DATASET_G))
        val insight = requireNotNull(result.firstOrNull { it.kind == InsightKind.TEMPERATURE_CORRELATION })
        assertThat(insight.priority).isEqualTo(40)
        assertThat(insight.chart).isEqualTo(InsightChart.CORRELATION_TEMPERATURE)
        assertThat(insight.params).containsExactly("positive", "true", "strength", "STRONG")
        assertThat(result.any { it.kind == InsightKind.ACTIVITY_CORRELATION }).isFalse()
    }

    @Test
    fun `temperature correlation does not fire with too few pairs or no relation`() {
        assertThat(find(InsightKind.TEMPERATURE_CORRELATION, input(temperature = DATASET_G.take(13)))).isNull()
        val unrelated = DATASET_G.mapIndexed { i, (x, _) -> x to (if (i % 2 == 0) 2000.0 else 2100.0) + (i % 3) * 5 }
        val weak = correlate(unrelated)
        assertThat(weak == null || kotlin.math.abs(weak.rho) < 0.4).isTrue()
        assertThat(find(InsightKind.TEMPERATURE_CORRELATION, input(temperature = unrelated))).isNull()
    }

    @Test
    fun `activity correlation fires and does not fire`() {
        val insight = requireNotNull(find(InsightKind.ACTIVITY_CORRELATION, input(activeMinutes = DATASET_G)))
        assertThat(insight.chart).isEqualTo(InsightChart.CORRELATION_ACTIVITY)
        assertThat(find(InsightKind.ACTIVITY_CORRELATION, input(activeMinutes = DATASET_G.take(5)))).isNull()
    }

    // CONSISTENCY

    @Test
    fun `consistency fires for very regular and very irregular days`() {
        val high = requireNotNull(find(InsightKind.CONSISTENCY, input(days = daysEndingYesterday(List(30) { 2000 }))))
        assertThat(high.priority).isEqualTo(30)
        assertThat(high.params).containsExactly("score", "100", "high", "true")
        val low = requireNotNull(
            find(
                InsightKind.CONSISTENCY,
                input(
                    days = daysEndingYesterday(
                        List(30) {
                            if (it % 2 ==
                                0
                            ) {
                                500
                            } else {
                                3500
                            }
                        },
                    ),
                ),
            ),
        )
        assertThat(low.params["high"]).isEqualTo("false")
    }

    @Test
    fun `consistency does not fire for a middling score`() {
        val days = daysEndingYesterday(List(14) { if (it % 2 == 0) 1500 else 2500 })
        val score = requireNotNull(consistencyScore(days, false))
        assertThat(score).isIn(41..79)
        assertThat(find(InsightKind.CONSISTENCY, input(days = days))).isNull()
    }

    // Ordering

    @Test
    fun `insights sort by priority then kind ordinal`() {
        val days = daysEndingYesterday(List(30) { 2000 })
        val kinds = buildInsights(input(days = days, temperature = DATASET_G, activeMinutes = DATASET_G)).map { it.kind }
        assertThat(kinds).containsExactly(
            InsightKind.STREAK,
            InsightKind.GOAL_HITS_LAST_7,
            InsightKind.TEMPERATURE_CORRELATION,
            InsightKind.ACTIVITY_CORRELATION,
            InsightKind.CONSISTENCY,
        ).inOrder()
    }

    @Test
    fun `no data gives no insights`() {
        assertThat(buildInsights(input())).isEmpty()
    }
}
