package casa.peterson.dhmo.core.domain.goal

/**
 * The constants of one version of the goal formula (TECHNICAL_PLAN §7.2).
 *
 * T1 adds one property per row of the §7.2 tables.
 */
interface GoalPolicy {
    val version: Int

    /** Used only when there is neither a profile nor an override. */
    val fallbackTargetMl: Int
}

object GoalPolicyV1 : GoalPolicy {
    override val version: Int = 1
    override val fallbackTargetMl: Int = 2000
}
