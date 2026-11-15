package casa.peterson.dhmo.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.database.entity.DailyGoalEntity
import casa.peterson.dhmo.core.database.entity.DayOverrideEntity
import casa.peterson.dhmo.core.model.GoalMode
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoalDaoTest {
    @get:Rule val rule = DatabaseTestRule()
    private val dao get() = rule.db.goalDao()

    private fun goal(day: java.time.LocalDate, target: Int) =
        DailyGoalEntity(day, target, null, GoalMode.CALCULATED, "{}", 0, 1, Instant.EPOCH)

    @Test
    fun `goal upsert replaces and range is ordered`() = runTest {
        dao.upsertGoals(listOf(goal(DAY.plusDays(1), 2100), goal(DAY, 2000)))
        dao.upsertGoal(goal(DAY, 2200))
        assertThat(dao.getGoal(DAY)!!.targetMl).isEqualTo(2200)
        assertThat(dao.observeGoal(DAY).first()!!.targetMl).isEqualTo(2200)
        assertThat(dao.getGoals(DAY, DAY.plusDays(1)).map { it.date }).isInStrictOrder()
        assertThat(dao.observeGoals(DAY, DAY).first()).hasSize(1)
        assertThat(dao.getAllGoals()).hasSize(2)
        dao.deleteAllGoals()
        assertThat(dao.getAllGoals()).isEmpty()
    }

    @Test
    fun `day override tombstones are hidden`() = runTest {
        dao.upsertOverride(DayOverrideEntity(DAY, 3000, "hot", Instant.EPOCH))
        assertThat(dao.getOverride(DAY)!!.targetMl).isEqualTo(3000)
        assertThat(dao.observeOverride(DAY).first()!!.reason).isEqualTo("hot")
        dao.softDeleteOverride(DAY, Instant.EPOCH.plusSeconds(9))
        assertThat(dao.getOverride(DAY)).isNull()
        assertThat(dao.getAllOverridesIncludingDeleted().single().deletedAt).isNotNull()
        dao.deleteAllOverrides()
        assertThat(dao.getAllOverridesIncludingDeleted()).isEmpty()
    }
}
