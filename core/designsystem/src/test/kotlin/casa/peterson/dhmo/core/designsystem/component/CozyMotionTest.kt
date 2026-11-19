package casa.peterson.dhmo.core.designsystem.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CozyMotionTest {
    @Test
    fun hopLeavesFromPlantedFeetAndReturnsToTheDrawingAtRest() {
        val rest = SpiritPose(0f, 0f, 0f, 0f, 0f)
        assertThat(sipPose(0f, false)).isEqualTo(rest)
        assertThat(sipPose(1f, false)).isEqualTo(rest)
        assertThat(sipPose(0.29f, false).height).isEqualTo(0f)
        assertThat(sipPose(0.29f, false).stretch).isLessThan(0f)
        assertThat(sipPose(0.38f, false).stretch).isGreaterThan(0f)
        assertThat(sipPose(0.69f, false).stretch).isLessThan(0f)
        assertThat(sipPose(0.78f, false).height).isLessThan(sipPose(0.5f, false).height)
        assertThat(sipPose(0.96f, false).height).isEqualTo(0f)
    }

    @Test
    fun goalHopIsTallerAndSharesTheSameLanding() {
        val sip = sipPose(0.5f, false)
        val goal = sipPose(0.5f, true)
        assertThat(goal.height).isGreaterThan(sip.height)
        assertThat(sipPose(0.69f, true)).isEqualTo(sipPose(0.69f, false))
    }
}
