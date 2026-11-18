package casa.peterson.dhmo.feature.insights.domain

import casa.peterson.dhmo.core.data.repository.ActivityRepository
import casa.peterson.dhmo.core.data.repository.IntakeRepository
import casa.peterson.dhmo.core.data.repository.WeatherRepository
import java.time.LocalDate
import javax.inject.Inject

enum class CorrelationDriver { TEMPERATURE, ACTIVE_MINUTES }

/**
 * Pairs `(driver value, intake in ml)` for the last [WINDOW_DAYS] days that had at least one drink logged, ending
 * yesterday. Days without a driver value are left out, so a missing forecast never counts as "0 degrees".
 */
class LoadCorrelationPairsUseCase @Inject constructor(
    private val intake: IntakeRepository,
    private val weather: WeatherRepository,
    private val activity: ActivityRepository,
) {
    suspend operator fun invoke(driver: CorrelationDriver, today: LocalDate): List<Pair<Double, Double>> {
        val from = today.minusDays(WINDOW_DAYS)
        val to = today.minusDays(1)
        val totals = intake.dailyTotals(from, to).filter { it.totalMl > 0 }.associate { it.date to it.totalMl.toDouble() }
        val drivers: Map<LocalDate, Double> = when (driver) {
            CorrelationDriver.TEMPERATURE -> weather.range(from, to).associate { it.date to it.maxApparentTempC }
            // With any activity data at all, a logged day without a session was a rest day, not a missing value.
            CorrelationDriver.ACTIVE_MINUTES -> activity.minutesPerDay(from, to).let { minutes ->
                if (minutes.isEmpty()) emptyMap() else totals.keys.associateWith { (minutes[it] ?: 0).toDouble() }
            }
        }
        return totals.mapNotNull { (day, ml) -> drivers[day]?.let { it to ml } }.sortedBy { it.first }
    }

    companion object {
        const val WINDOW_DAYS = 60L
    }
}
