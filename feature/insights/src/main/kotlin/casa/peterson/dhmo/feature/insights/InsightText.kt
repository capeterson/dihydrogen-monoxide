package casa.peterson.dhmo.feature.insights

import android.content.res.Resources
import casa.peterson.dhmo.core.domain.format.VolumeFormatter
import casa.peterson.dhmo.core.domain.stats.CorrelationStrength
import casa.peterson.dhmo.core.domain.stats.Insight
import casa.peterson.dhmo.core.domain.stats.InsightKind
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * The sentence for each [InsightKind] (§9.13), from the rule's string params.
 *
 * TREND: `changeMl` is the **total change** of the daily amount over the range (Theil–Sen slope per day × the range
 * length, rounded to 10 ml), not a rate. The plan's example "Up 120 ml/day over the last 8 weeks" meant "the daily
 * amount rose by 120 ml". The text says that unambiguously: "Your daily intake is up 120 ml over the last 8 weeks."
 */
object InsightText {
    fun format(insight: Insight, resources: Resources, units: VolumeUnit, locale: Locale): String {
        val p = Params(insight.params, units, locale)
        return when (insight.kind) {
            InsightKind.GOAL_HITS_LAST_7, InsightKind.STREAK, InsightKind.TREND, InsightKind.CONSISTENCY, InsightKind.ANOMALY ->
                progress(insight.kind, p, resources)

            else -> pattern(insight.kind, p, resources)
        }
    }

    /** Goal, streak, trend, consistency and anomaly sentences. */
    private fun progress(kind: InsightKind, p: Params, resources: Resources): String = when (kind) {
        InsightKind.GOAL_HITS_LAST_7 ->
            resources.getQuantityString(R.plurals.feature_insights_text_goal_hits, p.int("of"), p.int("met"), p.int("of"))

        InsightKind.STREAK -> resources.getQuantityString(
            if (p.flag("isLongest")) R.plurals.feature_insights_text_streak_longest else R.plurals.feature_insights_text_streak,
            p.int("days"),
            p.int("days"),
        )

        InsightKind.TREND -> resources.getQuantityString(
            if (p.raw("direction") == "UP") R.plurals.feature_insights_text_trend_up else R.plurals.feature_insights_text_trend_down,
            p.int("weeks"),
            p.volume("changeMl"),
            p.int("weeks"),
        )

        InsightKind.CONSISTENCY -> resources.getString(
            if (p.flag("high")) R.string.feature_insights_text_consistency_high else R.string.feature_insights_text_consistency_low,
            p.int("score"),
        )

        else -> resources.getString(
            R.string.feature_insights_text_anomaly,
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(p.locale).format(LocalDate.parse(p.raw("date"))),
            p.volume("totalMl"),
        )
    }

    /** Weekly and daily pattern sentences, and the correlations. */
    private fun pattern(kind: InsightKind, p: Params, resources: Resources): String = when (kind) {
        InsightKind.WEEKEND_VS_WEEKDAY -> resources.getString(
            if (p.flag("lower")) R.string.feature_insights_text_weekend_lower else R.string.feature_insights_text_weekend_higher,
            p.int("percent"),
        )

        InsightKind.WEAKEST_DAY -> resources.getString(
            R.string.feature_insights_text_weakest_day,
            DayOfWeek.valueOf(p.raw("day")).getDisplayName(TextStyle.FULL, p.locale),
            p.int("percentBelow"),
        )

        InsightKind.BIGGEST_GAP_BLOCK -> {
            val (start, end) = p.raw("block").split("-").map { it.toInt() }
            resources.getString(
                R.string.feature_insights_text_gap_block,
                resources.getString(blockName(start)),
                clock(start, p.locale),
                clock(end, p.locale),
            )
        }

        InsightKind.EVENING_SHARE ->
            resources.getString(R.string.feature_insights_text_evening_share, p.int("percent"), clock(EVENING_HOUR, p.locale))

        InsightKind.TEMPERATURE_CORRELATION -> correlation(
            resources,
            p,
            R.string.feature_insights_text_temperature_more,
            R.string.feature_insights_text_temperature_less,
        )

        else -> correlation(resources, p, R.string.feature_insights_text_activity_more, R.string.feature_insights_text_activity_less)
    }

    private class Params(private val values: Map<String, String>, val units: VolumeUnit, val locale: Locale) {
        fun raw(key: String): String = values.getValue(key)
        fun int(key: String): Int = raw(key).toInt()
        fun flag(key: String): Boolean = raw(key).toBooleanStrict()
        fun volume(key: String): String = VolumeFormatter.format(int(key), units, locale)
    }

    private fun correlation(resources: Resources, p: Params, more: Int, less: Int): String {
        val sentence = resources.getString(if (p.flag("positive")) more else less)
        val strength = when (CorrelationStrength.valueOf(p.raw("strength"))) {
            CorrelationStrength.STRONG -> R.string.feature_insights_strength_strong
            CorrelationStrength.MODERATE -> R.string.feature_insights_strength_moderate
            CorrelationStrength.WEAK, CorrelationStrength.NONE -> R.string.feature_insights_strength_weak
        }
        return resources.getString(R.string.feature_insights_text_correlation, sentence, resources.getString(strength))
    }

    private fun blockName(startHour: Int): Int = when (startHour) {
        MORNING_START -> R.string.feature_insights_block_morning
        MIDDAY_START -> R.string.feature_insights_block_midday
        AFTERNOON_START -> R.string.feature_insights_block_afternoon
        else -> R.string.feature_insights_block_evening
    }

    private fun clock(hour: Int, locale: Locale): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(LocalTime.of(hour % HOURS_PER_DAY, 0))

    private const val MORNING_START = 8
    private const val MIDDAY_START = 11
    private const val AFTERNOON_START = 14
    private const val EVENING_HOUR = 18
    private const val HOURS_PER_DAY = 24
}
