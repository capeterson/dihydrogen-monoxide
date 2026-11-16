package casa.peterson.dhmo.feature.insights.domain

import casa.peterson.dhmo.core.data.repository.GoalRepository
import casa.peterson.dhmo.core.data.repository.IntakeRepository
import casa.peterson.dhmo.core.domain.stats.DayStat
import casa.peterson.dhmo.core.domain.stats.buildDayStats
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/** `buildDayStats` over `[from, to]` from the daily totals and the goal snapshots (TECHNICAL_PLAN §6.3, §9.1). */
class LoadStatsRangeUseCase @Inject constructor(private val intake: IntakeRepository, private val goals: GoalRepository) {
    suspend operator fun invoke(from: LocalDate, to: LocalDate): List<DayStat> {
        if (to.isBefore(from)) return emptyList()
        return buildDayStats(from, to, intake.dailyTotals(from, to), goals.goals(from, to).associate { it.date to it.targetMl })
    }

    /** Re-emits whenever an entry or a goal snapshot in the range changes. */
    fun observe(from: LocalDate, to: LocalDate): Flow<List<DayStat>> {
        if (to.isBefore(from)) return flowOf(emptyList())
        return combine(intake.observeDailyTotals(from, to), goals.observeGoals(from, to)) { totals, snapshots ->
            buildDayStats(from, to, totals, snapshots.associate { it.date to it.targetMl })
        }
    }
}
