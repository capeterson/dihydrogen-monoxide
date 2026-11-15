@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.core.domain.goal

import casa.peterson.dhmo.core.model.GoalBreakdown
import casa.peterson.dhmo.core.model.GoalInput

@Suppress("UnusedPrivateProperty") // used by the T1 implementation
class GoalCalculator(private val policy: GoalPolicy = GoalPolicyV1) {
    fun calculate(input: GoalInput): GoalBreakdown = TODO("T1")
}
