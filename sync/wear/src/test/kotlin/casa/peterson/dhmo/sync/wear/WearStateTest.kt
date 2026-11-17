package casa.peterson.dhmo.sync.wear

import casa.peterson.dhmo.core.model.ClampFlag
import casa.peterson.dhmo.core.model.Container
import casa.peterson.dhmo.core.model.ContainerIcon
import casa.peterson.dhmo.core.model.DailyGoal
import casa.peterson.dhmo.core.model.DailyTotal
import casa.peterson.dhmo.core.model.GoalBreakdown
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.core.wear.WearContainer
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Test

class WearStateTest {
    private val day = LocalDate.of(2026, 11, 15)
    private val t = Instant.parse("2026-11-15T08:00:00Z")

    private fun container(name: String, ml: Int, order: Int) = Container(UUID.randomUUID(), name, ml, ContainerIcon.GLASS, order, t)

    @Test
    fun `state carries total, effective goal, units and the first three containers in order`() {
        val goal = DailyGoal(day, 2400, 2300, GoalMode.DAY_OVERRIDE, GoalBreakdown(1, 30, emptyList(), 0, 0, 0, ClampFlag.NONE), 0, 1, t)
        val containers =
            listOf(container("Flask", 750, 3), container("Glass", 250, 0), container("Mug", 300, 2), container("Bottle", 500, 1))

        val state = buildWearState(day, DailyTotal(day, 1200, 4, t, t), goal, VolumeUnit.L, containers, 99)

        assertThat(state.day).isEqualTo("2026-11-15")
        assertThat(state.totalMl).isEqualTo(1200)
        assertThat(state.goalMl).isEqualTo(2400)
        assertThat(state.unit).isEqualTo(VolumeUnit.L)
        assertThat(
            state.containers,
        ).containsExactly(WearContainer("Glass", 250), WearContainer("Bottle", 500), WearContainer("Mug", 300)).inOrder()
        assertThat(state.updatedAtMillis).isEqualTo(99)
    }

    @Test
    fun `an empty day has zero total and no goal`() {
        val state = buildWearState(day, null, null, VolumeUnit.ML, emptyList(), 0)
        assertThat(state.totalMl).isEqualTo(0)
        assertThat(state.goalMl).isEqualTo(0)
        assertThat(state.containers).isEmpty()
    }
}
