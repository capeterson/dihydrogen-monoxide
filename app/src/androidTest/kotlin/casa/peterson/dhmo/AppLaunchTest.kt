package casa.peterson.dhmo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Smoke test: a fresh install starts and shows the onboarding welcome screen. */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FreshInstallRule()).around(composeRule)

    @Test
    fun appStartsOnTheOnboardingWelcomeScreen() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText("Welcome to Dihydrogen Monoxide")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Welcome to Dihydrogen Monoxide").assertIsDisplayed()
    }
}
