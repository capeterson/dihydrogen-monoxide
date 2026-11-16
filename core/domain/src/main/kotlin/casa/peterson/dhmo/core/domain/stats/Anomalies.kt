package casa.peterson.dhmo.core.domain.stats

import java.time.LocalDate
import kotlin.math.abs

private const val MAD_SCALE = 1.4826
private const val SD_FLOOR_FRACTION = 0.10

enum class AnomalyDirection { HIGH, LOW }

data class Anomaly(val date: LocalDate, val totalMl: Int, val medianMl: Double, val robustSdMl: Double, val direction: AnomalyDirection)

fun anomalies(days: List<DayStat>, windowDays: Int = 30, minSamples: Int = 7, threshold: Double = 2.5): List<Anomaly> {
    val result = ArrayList<Anomaly>()
    for (day in days.filter { it.logged }) {
        val earliest = day.date.minusDays(windowDays.toLong())
        val window = days
            .filter { it.logged && !it.date.isBefore(earliest) && it.date.isBefore(day.date) }
            .map { it.totalMl.toDouble() }
        if (window.size < minSamples) continue
        val median = Descriptive.median(window)
        val mad = Descriptive.median(window.map { abs(it - median) })
        val robustSd = maxOf(MAD_SCALE * mad, SD_FLOOR_FRACTION * median)
        val deviation = day.totalMl - median
        if (abs(deviation) > threshold * robustSd) {
            val direction = if (deviation > 0) AnomalyDirection.HIGH else AnomalyDirection.LOW
            result.add(Anomaly(day.date, day.totalMl, median, robustSd, direction))
        }
    }
    return result
}
