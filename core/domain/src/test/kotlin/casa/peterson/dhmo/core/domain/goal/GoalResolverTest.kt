package casa.peterson.dhmo.core.domain.goal

import casa.peterson.dhmo.core.model.ClampFlag
import casa.peterson.dhmo.core.model.GoalBreakdown
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.model.ResolvedGoal
import casa.peterson.dhmo.core.model.settings.GlobalOverride
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/** O1-O9 of TECHNICAL_PLAN §7.5. */
class GoalResolverTest {
    private val wed = LocalDate.of(2026, 6, 10)
    private val sat = LocalDate.of(2026, 6, 13)
    private val sun = LocalDate.of(2026, 6, 14)
    private val breakdown = GoalBreakdown(1, 31, emptyList(), 2310, 2310, 2300, ClampFlag.NONE)
    private val saturday = mapOf("SATURDAY" to 3000)

    private fun resolve(date: LocalDate, day: Int?, global: GlobalOverride, b: GoalBreakdown? = breakdown) =
        GoalResolver.resolve(date, b, day, global)

    @Test fun o1() = assertThat(resolve(wed, null, GlobalOverride()))
        .isEqualTo(ResolvedGoal(2300, 2300, GoalMode.CALCULATED))

    @Test fun o2() = assertThat(resolve(wed, null, GlobalOverride(true, 2500)))
        .isEqualTo(ResolvedGoal(2500, 2300, GoalMode.GLOBAL_OVERRIDE))

    @Test fun o3() = assertThat(resolve(sat, null, GlobalOverride(true, 2500, saturday)))
        .isEqualTo(ResolvedGoal(3000, 2300, GoalMode.GLOBAL_OVERRIDE))

    @Test fun o4() = assertThat(resolve(sun, null, GlobalOverride(true, 2500, saturday)))
        .isEqualTo(ResolvedGoal(2500, 2300, GoalMode.GLOBAL_OVERRIDE))

    @Test fun o5() = assertThat(resolve(sat, 4000, GlobalOverride(true, 2500, saturday)))
        .isEqualTo(ResolvedGoal(4000, 2300, GoalMode.DAY_OVERRIDE))

    @Test fun o6() = assertThat(resolve(wed, 4025, GlobalOverride()))
        .isEqualTo(ResolvedGoal(4025, 2300, GoalMode.DAY_OVERRIDE))

    @Test fun o7() = assertThat(resolve(wed, null, GlobalOverride(false, 2500)))
        .isEqualTo(ResolvedGoal(2300, 2300, GoalMode.CALCULATED))

    @Test fun o8() = assertThat(resolve(wed, null, GlobalOverride(true, 1800), null))
        .isEqualTo(ResolvedGoal(1800, null, GoalMode.GLOBAL_OVERRIDE))

    @Test fun o9() = assertThat(resolve(wed, null, GlobalOverride(), null))
        .isEqualTo(ResolvedGoal(2000, null, GoalMode.CALCULATED))
}
