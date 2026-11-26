package casa.peterson.dhmo.core.data.usecase.goal

import android.util.Log
import casa.peterson.dhmo.core.common.suspendRunCatching
import casa.peterson.dhmo.core.data.repository.GoalRepository
import casa.peterson.dhmo.core.data.repository.ReminderScheduler
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.repository.WidgetUpdater
import casa.peterson.dhmo.core.domain.goal.GoalCalculator
import casa.peterson.dhmo.core.domain.goal.GoalPolicy
import casa.peterson.dhmo.core.domain.goal.GoalResolver
import casa.peterson.dhmo.core.model.ClampFlag
import casa.peterson.dhmo.core.model.DailyGoal
import casa.peterson.dhmo.core.model.GoalBreakdown
import casa.peterson.dhmo.core.model.RescheduleReason
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Saves a snapshot only when its contents have changed. With [invoke]'s `notifyReminders` (today's snapshot), a changed
 * target also reschedules reminders and repaints the widgets; a backfill of past days does neither.
 */
class SnapshotDailyGoalUseCase @Inject constructor(
    private val input: BuildGoalInputUseCase,
    private val calculator: GoalCalculator,
    private val policy: GoalPolicy,
    private val goals: GoalRepository,
    private val settings: SettingsRepository,
    private val reminders: ReminderScheduler,
    private val clock: Clock,
    private val widgets: WidgetUpdater,
) {
    suspend operator fun invoke(day: LocalDate, notifyReminders: Boolean = true): DailyGoal {
        val current = settings.current()
        val breakdown = input(day)?.let(calculator::calculate)
        val override = goals.observeDayOverride(day).first()
        val resolved = GoalResolver.resolve(day, breakdown, override?.targetMl, current.goal.globalOverride, policy)
        val existing = goals.goal(day)
        val snapshot = DailyGoal(
            date = day,
            targetMl = resolved.targetMl,
            calculatedMl = resolved.calculatedMl,
            mode = resolved.mode,
            breakdown = breakdown ?: GoalBreakdown(policy.version, 0, emptyList(), 0, 0, 0, ClampFlag.NONE),
            dayStartMinutes = current.dayStartMinutes,
            policyVersion = policy.version,
            updatedAt = existing?.updatedAt ?: clock.instant(),
        )
        if (snapshot == existing) return existing
        val updated = snapshot.copy(updatedAt = clock.instant())
        goals.saveSnapshot(updated)
        if (notifyReminders && existing?.targetMl != updated.targetMl) {
            reminders.reschedule(RescheduleReason.GOAL_CHANGED)
            suspendRunCatching { widgets.updateAll() }.onFailure { Log.w(TAG, "Widget refresh failed", it) }
        }
        return updated
    }

    private companion object {
        const val TAG = "SnapshotDailyGoal"
    }
}
