package casa.peterson.dhmo.core.domain.stats

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StreaksTest {
    private fun goal(days: List<DayStat>, freeze: Boolean, inProgress: Boolean) = goalStreak(days, freeze, inProgress)

    @Test
    fun `goal streak without freeze`() {
        assertThat(goal(datasetA(), freeze = false, inProgress = false)).isEqualTo(Streaks(current = 1, longest = 2))
    }

    @Test
    fun `goal streak with freeze keeps Wednesday 11 alive`() {
        assertThat(goal(datasetA(), freeze = true, inProgress = false)).isEqualTo(Streaks(current = 1, longest = 4))
    }

    @Test
    fun `last day below goal breaks the streak`() {
        val days = daysOf(DATASET_A_TOTALS.dropLast(1) + 500)
        assertThat(goal(days, freeze = false, inProgress = false)).isEqualTo(Streaks(current = 0, longest = 2))
    }

    @Test
    fun `today in progress does not break an already broken streak`() {
        val days = daysOf(DATASET_A_TOTALS.dropLast(1) + 500)
        assertThat(goal(days, freeze = false, inProgress = true)).isEqualTo(Streaks(current = 0, longest = 2))
    }

    @Test
    fun `today in progress does not break a running streak`() {
        val days = daysOf(DATASET_A_TOTALS.take(12) + 500)
        assertThat(goal(days, freeze = false, inProgress = true)).isEqualTo(Streaks(current = 2, longest = 2))
        assertThat(goal(days, freeze = false, inProgress = false)).isEqualTo(Streaks(current = 0, longest = 2))
    }

    @Test
    fun `logging streak`() {
        assertThat(loggingStreak(datasetA(), weeklyFreeze = false, todayInProgress = false))
            .isEqualTo(Streaks(current = 11, longest = 11))
        assertThat(loggingStreak(datasetA(), weeklyFreeze = true, todayInProgress = false))
            .isEqualTo(Streaks(current = 13, longest = 13))
    }

    @Test
    fun `one freeze per ISO week`() {
        // Mon..Wed met, Thu and Fri missed in the same week: only the first miss is frozen.
        val days = daysOf(listOf(2000, 2000, 2000, 0, 0, 2000, 2000))
        assertThat(goal(days, freeze = true, inProgress = false)).isEqualTo(Streaks(current = 2, longest = 3))
    }

    @Test
    fun `empty list`() {
        assertThat(goal(emptyList(), freeze = true, inProgress = true)).isEqualTo(Streaks(0, 0))
    }
}
