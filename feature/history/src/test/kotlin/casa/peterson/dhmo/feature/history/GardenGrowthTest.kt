package casa.peterson.dhmo.feature.history

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GardenGrowthTest {
    @Test
    fun emptyDaysRemainSoilAndEveryPositiveAmountGetsASprout() {
        assertThat(gardenGrowth(0, 2000)).isEqualTo(0f)
        assertThat(gardenGrowth(1, 2000)).isGreaterThan(0f)
    }

    @Test
    fun growthReflectsTheActualGoalWithoutOverflowOrInventingAMissingGoal() {
        assertThat(gardenGrowth(1000, 2000)).isEqualTo(0.5f)
        assertThat(gardenGrowth(3000, 2000)).isEqualTo(1f)
        assertThat(gardenGrowth(1000, null)).isLessThan(1f)
        assertThat(gardenGrowth(1000, 0)).isLessThan(1f)
    }
}
