package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.designsystem.theme.DhmoTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressRingSemanticsTest {
    @get:Rule val composeRule = createComposeRule()

    private fun show(progress: Float) {
        composeRule.setContent { DhmoTheme(dynamicColor = false) { ProgressRing(progress = progress) } }
    }

    private fun hasProgress(expected: Float) = SemanticsMatcher("progress is $expected") {
        it.config[SemanticsProperties.ProgressBarRangeInfo].current == expected
    }

    @Test
    fun announcesPercentOfGoal() {
        show(0.42f)
        composeRule.onNodeWithContentDescription("42 percent of goal").assertExists()
    }

    @Test
    fun announcesZeroPercentWhenEmpty() {
        show(0f)
        composeRule.onNodeWithContentDescription("0 percent of goal").assertContentDescriptionEquals("0 percent of goal")
    }

    @Test
    fun capsTheArcAtOneHundredPercentButReportsTheRealPercent() {
        show(1.5f)
        composeRule.onNodeWithContentDescription("150 percent of goal").assert(hasProgress(1f))
    }

    @Test
    fun exposesTheFractionAsProgress() {
        show(0.25f)
        composeRule.onNodeWithContentDescription("25 percent of goal").assert(hasProgress(0.25f))
    }

    @Test
    fun clampsInvalidProgress() {
        assertThat(ringFraction(-1f)).isEqualTo(0f)
        assertThat(ringFraction(Float.NaN)).isEqualTo(0f)
        assertThat(ringFraction(3f)).isEqualTo(1f)
        assertThat(ringPercent(-1f)).isEqualTo(0)
        assertThat(ringPercent(Float.NaN)).isEqualTo(0)
        assertThat(ringPercent(0.995f)).isEqualTo(100)
    }
}
