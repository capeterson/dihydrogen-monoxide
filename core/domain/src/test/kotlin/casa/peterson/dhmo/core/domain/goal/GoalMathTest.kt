package casa.peterson.dhmo.core.domain.goal

import casa.peterson.dhmo.core.model.ClampFlag
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class GoalMathTest {
    @Test
    fun roundTo50() {
        val cases = mapOf(
            2024 to 2000, 2025 to 2050, 2049 to 2050, 2050 to 2050, 2074 to 2050, 2075 to 2100,
            0 to 0, 24 to 0, 25 to 50,
        )
        for ((input, expected) in cases) {
            assertThat(GoalMath.roundTo50(input)).isEqualTo(expected)
        }
    }

    @Test
    fun clampAndRound() {
        assertThat(GoalMath.clampAndRound(900, 1000, 6000)).isEqualTo(Triple(1000, 1000, ClampFlag.LOW))
        assertThat(GoalMath.clampAndRound(1000, 1000, 6000)).isEqualTo(Triple(1000, 1000, ClampFlag.NONE))
        assertThat(GoalMath.clampAndRound(6000, 1000, 6000)).isEqualTo(Triple(6000, 6000, ClampFlag.NONE))
        assertThat(GoalMath.clampAndRound(6001, 1000, 6000)).isEqualTo(Triple(6000, 6000, ClampFlag.HIGH))
        assertThat(GoalMath.clampAndRound(590, 600, 3000)).isEqualTo(Triple(600, 600, ClampFlag.LOW))
    }

    @Test
    fun weightBaseMl() {
        assertThat(GoalMath.weightBaseMl(70.04, 33)).isEqualTo(2310)
        assertThat(GoalMath.weightBaseMl(70.05, 33)).isEqualTo(2313)
    }

    @Test
    fun ageYears() {
        val on = LocalDate.of(2026, 6, 10)
        assertThat(GoalMath.ageYears(LocalDate.of(2012, 6, 11), on)).isEqualTo(13)
        assertThat(GoalMath.ageYears(LocalDate.of(2012, 6, 10), on)).isEqualTo(14)
        assertThat(GoalMath.ageYears(LocalDate.of(2000, 2, 29), LocalDate.of(2026, 2, 28))).isEqualTo(25)
        assertThat(GoalMath.ageYears(LocalDate.of(2000, 2, 29), LocalDate.of(2026, 3, 1))).isEqualTo(26)
        assertThat(GoalMath.ageYears(LocalDate.of(2030, 1, 1), on)).isEqualTo(0)
    }
}
