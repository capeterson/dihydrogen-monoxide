package casa.peterson.dhmo.core.domain.goal

import casa.peterson.dhmo.core.model.GoalBreakdown
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.model.ResolvedGoal
import casa.peterson.dhmo.core.model.settings.GlobalOverride
import java.time.LocalDate

object GoalResolver {
    /**
     * @param breakdown null when there is no profile
     * @param dayOverrideMl the `day_override` row for this date, tombstones excluded
     */
    fun resolve(
        date: LocalDate,
        breakdown: GoalBreakdown?,
        dayOverrideMl: Int?,
        global: GlobalOverride,
        policy: GoalPolicy = GoalPolicyV1,
    ): ResolvedGoal = when {
        dayOverrideMl != null -> ResolvedGoal(dayOverrideMl, breakdown?.calculatedMl, GoalMode.DAY_OVERRIDE)

        global.enabled -> ResolvedGoal(
            global.perWeekdayMl[date.dayOfWeek.name] ?: global.targetMl,
            breakdown?.calculatedMl,
            GoalMode.GLOBAL_OVERRIDE,
        )

        breakdown != null -> ResolvedGoal(breakdown.calculatedMl, breakdown.calculatedMl, GoalMode.CALCULATED)

        else -> ResolvedGoal(policy.fallbackTargetMl, null, GoalMode.CALCULATED)
    }
}
