package casa.peterson.dhmo.feature.insights.domain

import casa.peterson.dhmo.core.data.repository.IntakeRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.time.DayTicker
import casa.peterson.dhmo.core.domain.stats.Insight
import casa.peterson.dhmo.core.domain.stats.InsightInput
import casa.peterson.dhmo.core.domain.stats.TimedIntake
import casa.peterson.dhmo.core.domain.stats.buildInsights
import casa.peterson.dhmo.core.model.IntakeEntry
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Assembles [InsightInput] (days ending yesterday, the last 14 days' entries) and calls `buildInsights` (§9.13).
 *
 * The two correlation inputs come from [LoadCorrelationPairsUseCase] and stay empty until weather or activity data
 * exists, so those insights only appear once there are 14 or more matching days.
 */
class ObserveInsightsUseCase @Inject constructor(
    private val ticker: DayTicker,
    private val settings: SettingsRepository,
    private val loadStats: LoadStatsRangeUseCase,
    private val intake: IntakeRepository,
    private val correlationPairs: LoadCorrelationPairsUseCase,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<List<Insight>> = combine(ticker.today(), settings.settings) { today, current -> today to current }
        .distinctUntilChanged()
        .flatMapLatest { (today, current) ->
            val yesterday = today.minusDays(1)
            combine(
                loadStats.observe(today.minusDays(DAYS_WINDOW), yesterday),
                intake.observeRange(today.minusDays(ENTRIES_WINDOW), yesterday),
            ) { days, entries ->
                buildInsights(
                    InsightInput(
                        today = today,
                        days = days,
                        entries = entries.map(IntakeEntry::toTimedIntake),
                        temperature = correlationPairs(CorrelationDriver.TEMPERATURE, today),
                        activeMinutes = correlationPairs(CorrelationDriver.ACTIVE_MINUTES, today),
                        dayStartMinutes = current.dayStartMinutes,
                        includeNoLogDays = current.stats.includeNoLogDays,
                        weeklyFreeze = current.stats.weeklyStreakFreeze,
                    ),
                )
            }
        }

    private companion object {
        const val DAYS_WINDOW = 90L
        const val ENTRIES_WINDOW = 14L
    }
}

/** The local clock minute of the entry at its own offset (§9.9). */
fun IntakeEntry.toTimedIntake(): TimedIntake {
    val local = instant.atOffset(zoneOffset).toLocalTime()
    return TimedIntake(hydrationDay, local.hour * MINUTES_PER_HOUR + local.minute, volumeMl)
}

private const val MINUTES_PER_HOUR = 60
