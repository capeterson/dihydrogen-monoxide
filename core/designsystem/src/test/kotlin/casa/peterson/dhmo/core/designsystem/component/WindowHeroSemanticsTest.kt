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
class WindowHeroSemanticsTest {
    @get:Rule val composeRule = createComposeRule()

    private fun show(progress: Float) {
        composeRule.setContent { DhmoTheme(dynamicColor = false) { WindowHero(progress = progress) } }
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
        assertThat(jarFraction(-1f)).isEqualTo(0f)
        assertThat(jarFraction(Float.NaN)).isEqualTo(0f)
        assertThat(jarFraction(3f)).isEqualTo(1f)
        assertThat(jarPercent(-1f)).isEqualTo(0)
        assertThat(jarPercent(Float.NaN)).isEqualTo(0)
        assertThat(jarPercent(0.995f)).isEqualTo(100)
    }
}
