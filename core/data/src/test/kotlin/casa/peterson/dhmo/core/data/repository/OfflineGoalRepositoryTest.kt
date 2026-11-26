package casa.peterson.dhmo.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import casa.peterson.dhmo.core.data.RoomTestEnvironment
import casa.peterson.dhmo.core.model.ClampFlag
import casa.peterson.dhmo.core.model.ComponentKind
import casa.peterson.dhmo.core.model.DailyGoal
import casa.peterson.dhmo.core.model.GoalBreakdown
import casa.peterson.dhmo.core.model.GoalComponent
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.testing.MutableClock
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineGoalRepositoryTest {
    @get:Rule val env = RoomTestEnvironment()

    private val clock = MutableClock(Instant.parse("2026-06-10T08:00:00Z"))

    private fun repository() = OfflineGoalRepository(env.db.goalDao(), clock)

    private suspend fun failure(block: suspend () -> Unit): Throwable? = runCatching { block() }.exceptionOrNull()

    private val day = LocalDate.of(2026, 6, 10)

    private fun goal(date: LocalDate, targetMl: Int = 2150, mode: GoalMode = GoalMode.CALCULATED) = DailyGoal(
        date = date,
        targetMl = targetMl,
        calculatedMl = if (mode == GoalMode.CALCULATED) targetMl else null,
        mode = mode,
        breakdown = GoalBreakdown(
            policyVersion = 1,
            ageYears = 36,
            components = listOf(
                GoalComponent(ComponentKind.BASE_WEIGHT, 1950, applied = true, detail = mapOf("weightKg" to "65.0")),
                GoalComponent(ComponentKind.ALTITUDE, 250, applied = false),
            ),
            rawMl = 1950,
            clampedMl = 1950,
            calculatedMl = 1950,
            clamp = ClampFlag.NONE,
        ),
        dayStartMinutes = 0,
        policyVersion = 1,
        updatedAt = clock.instant(),
    )

    @Test
    fun `a snapshot round trips including the breakdown`() = runTest {
        val repo = repository()
        val saved = goal(day)
        repo.saveSnapshot(saved)

        assertThat(repo.goal(day)).isEqualTo(saved)
        assertThat(repo.goal(day.plusDays(1))).isNull()
    }

    @Test
    fun `saving a snapshot again replaces it`() = runTest {
        val repo = repository()
        repo.saveSnapshot(goal(day, 2000))
        repo.saveSnapshot(goal(day, 2500, GoalMode.GLOBAL_OVERRIDE))
        val stored = repo.goal(day)!!
        assertThat(stored.targetMl).isEqualTo(2500)
        assertThat(stored.calculatedMl).isNull()
        assertThat(stored.mode).isEqualTo(GoalMode.GLOBAL_OVERRIDE)
    }

    @Test
    fun `goals and observers cover a range`() = runTest {
        val repo = repository()
        (0..4).forEach { repo.saveSnapshot(goal(day.plusDays(it.toLong()), 2000 + it * 50)) }

        assertThat(repo.goals(day.plusDays(1), day.plusDays(3)).map { it.targetMl }).containsExactly(2050, 2100, 2150).inOrder()
        repo.observeGoal(day).test {
            assertThat(awaitItem()!!.targetMl).isEqualTo(2000)
            repo.saveSnapshot(goal(day, 2300))
            assertThat(awaitItem()!!.targetMl).isEqualTo(2300)
            cancelAndIgnoreRemainingEvents()
        }
        repo.observeGoals(day, day.plusDays(1)).test {
            assertThat(awaitItem()).hasSize(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a day override can be set replaced and cleared`() = runTest {
        val repo = repository()
        repo.observeDayOverride(day).test {
            assertThat(awaitItem()).isNull()
            repo.setDayOverride(day, 3000, "hike")
            val set = awaitItem()!!
            assertThat(set.targetMl).isEqualTo(3000)
            assertThat(set.reason).isEqualTo("hike")
            assertThat(set.updatedAt).isEqualTo(clock.instant())

            clock.advanceBy(Duration.ofMinutes(1))
            repo.setDayOverride(day, 2800, null)
            assertThat(awaitItem()!!.targetMl).isEqualTo(2800)

            repo.clearDayOverride(day)
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
        // The tombstone survives for merges.
        assertThat(env.db.goalDao().getAllOverridesIncludingDeleted().single().deletedAt).isNotNull()
    }

    @Test
    fun `setting an override again after clearing revives it`() = runTest {
        val repo = repository()
        repo.setDayOverride(day, 3000, null)
        repo.clearDayOverride(day)
        repo.setDayOverride(day, 2600, null)
        assertThat(env.db.goalDao().getOverride(day)!!.targetMl).isEqualTo(2600)
    }

    @Test
    fun `override targets are limited to 250 to 10000`() = runTest {
        val repo = repository()
        assertThat(failure { repo.setDayOverride(day, 249, null) }).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(failure { repo.setDayOverride(day, 10_001, null) }).isInstanceOf(IllegalArgumentException::class.java)
        repo.setDayOverride(day, 250, null)
        repo.setDayOverride(day, 10_000, null)
        assertThat(env.db.goalDao().getOverride(day)!!.targetMl).isEqualTo(10_000)
    }
}
