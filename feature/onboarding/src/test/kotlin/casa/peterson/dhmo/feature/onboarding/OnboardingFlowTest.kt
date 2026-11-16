package casa.peterson.dhmo.feature.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun forwardAndBackAndConditionSelection() {
        var selected = GoalChoice.CALCULATED
        compose.setContent {
            var page by remember { mutableStateOf(OnboardingPage.WELCOME) }
            var state by remember { mutableStateOf(OnboardingState(loading = false)) }
            MaterialTheme {
                OnboardingPageContent(
                    page = page,
                    state = state,
                    preview = null,
                    change = {
                        state = it(state)
                        selected = state.choice
                    },
                    next = { page = if (page == OnboardingPage.WELCOME) OnboardingPage.DISCLAIMER else OnboardingPage.GOAL_MODE },
                    back = { page = OnboardingPage.WELCOME },
                    allowNotifications = {},
                    skipNotifications = {},
                )
            }
        }
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Before you begin").assertExists()
        compose.onNodeWithText("I have a condition or fluid restriction").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("I’ll set my own goal").assertExists()
        compose.runOnIdle { assertThat(selected).isEqualTo(GoalChoice.MANUAL) }
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Welcome to Dihydrogen Monoxide").assertExists()
    }
}
