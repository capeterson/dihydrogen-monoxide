package casa.peterson.dhmo.feature.insights

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.domain.stats.Insight
import casa.peterson.dhmo.core.domain.stats.InsightChart
import casa.peterson.dhmo.core.domain.stats.InsightKind
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InsightTextTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    private fun text(kind: InsightKind, vararg params: Pair<String, String>, units: VolumeUnit = VolumeUnit.ML) =
        InsightText.format(Insight(kind, 0, params.toMap(), InsightChart.WEEK), resources, units, Locale.US)

    /** One example per kind, with the params `buildInsights` produces (§9.13). */
    private val examples: Map<InsightKind, Array<Pair<String, String>>> = mapOf(
        InsightKind.GOAL_HITS_LAST_7 to arrayOf("met" to "6", "of" to "7"),
        InsightKind.STREAK to arrayOf("days" to "5", "isLongest" to "false"),
        InsightKind.TREND to arrayOf("direction" to "UP", "changeMl" to "120", "weeks" to "8"),
        InsightKind.WEEKEND_VS_WEEKDAY to arrayOf("percent" to "21", "lower" to "true"),
        InsightKind.WEAKEST_DAY to arrayOf("day" to "SUNDAY", "percentBelow" to "18"),
        InsightKind.BIGGEST_GAP_BLOCK to arrayOf("block" to "14-17"),
        InsightKind.EVENING_SHARE to arrayOf("percent" to "45"),
        InsightKind.CONSISTENCY to arrayOf("score" to "84", "high" to "true"),
        InsightKind.ANOMALY to arrayOf("date" to "2026-03-10", "totalMl" to "5000"),
        InsightKind.TEMPERATURE_CORRELATION to arrayOf("positive" to "true", "strength" to "STRONG"),
        InsightKind.ACTIVITY_CORRELATION to arrayOf("positive" to "false", "strength" to "MODERATE"),
    )

    @Test
    fun everyKindFormats() {
        assertThat(examples.keys).containsExactlyElementsIn(InsightKind.entries)
        InsightKind.entries.forEach { kind ->
            val formatted = text(kind, *examples.getValue(kind))
            assertThat(formatted).isNotEmpty()
            assertThat(formatted).doesNotContainMatch("%[0-9]*\\$?[sd]")
        }
    }

    @Test
    fun matchesThePlanExamples() {
        assertThat(text(InsightKind.GOAL_HITS_LAST_7, *examples.getValue(InsightKind.GOAL_HITS_LAST_7)))
            .isEqualTo("You’ve hit your goal 6 of the last 7 days.")
        assertThat(text(InsightKind.STREAK, *examples.getValue(InsightKind.STREAK))).isEqualTo("You’re on a 5-day streak.")
        assertThat(text(InsightKind.STREAK, "days" to "9", "isLongest" to "true")).isEqualTo("You’re on a 9-day streak, your longest yet.")
        assertThat(text(InsightKind.WEEKEND_VS_WEEKDAY, *examples.getValue(InsightKind.WEEKEND_VS_WEEKDAY)))
            .isEqualTo("Your weekend average is 21% lower than weekdays.")
        assertThat(text(InsightKind.WEEKEND_VS_WEEKDAY, "percent" to "16", "lower" to "false"))
            .isEqualTo("Your weekend average is 16% higher than weekdays.")
        assertThat(text(InsightKind.WEAKEST_DAY, *examples.getValue(InsightKind.WEAKEST_DAY)))
            .isEqualTo("Sunday is usually your lowest day, 18% below average.")
        assertThat(text(InsightKind.BIGGEST_GAP_BLOCK, *examples.getValue(InsightKind.BIGGEST_GAP_BLOCK)))
            .isEqualTo("Afternoons (2:00\u202fPM–5:00\u202fPM) are your biggest gap.")
        assertThat(text(InsightKind.EVENING_SHARE, *examples.getValue(InsightKind.EVENING_SHARE)))
            .isEqualTo("You drink 45% of your water after 6:00\u202fPM.")
        assertThat(text(InsightKind.TEMPERATURE_CORRELATION, *examples.getValue(InsightKind.TEMPERATURE_CORRELATION)))
            .isEqualTo("On hotter days you tended to drink more (strong link).")
    }

    @Test
    fun trendStatesTheTotalChangeOfTheDailyAmountNotARate() {
        // changeMl is slope × range length: how much the daily amount changed over the 8 weeks (handoff decision).
        assertThat(text(InsightKind.TREND, *examples.getValue(InsightKind.TREND)))
            .isEqualTo("Your daily intake is up 120 ml over the last 8 weeks.")
        assertThat(text(InsightKind.TREND, "direction" to "DOWN", "changeMl" to "250", "weeks" to "8"))
            .isEqualTo("Your daily intake is down 250 ml over the last 8 weeks.")
    }

    @Test
    fun volumesFollowTheUserUnit() {
        assertThat(text(InsightKind.ANOMALY, *examples.getValue(InsightKind.ANOMALY), units = VolumeUnit.US_FL_OZ)).contains("fl oz")
        assertThat(text(InsightKind.ANOMALY, *examples.getValue(InsightKind.ANOMALY))).contains("Mar 10, 2026")
    }

    @Test
    fun datasetAWeekendInsightRoundsTo21() {
        val weekend = DatasetA.ready(RangeSelection.forRange(InsightsRange.WEEK, DatasetA.last)).insights
            .single { it.kind == InsightKind.WEEKEND_VS_WEEKDAY }
        assertThat(weekend.params).containsEntry("percent", "21")
        assertThat(InsightText.format(weekend, resources, VolumeUnit.ML, Locale.US))
            .isEqualTo("Your weekend average is 21% lower than weekdays.")
    }
}
