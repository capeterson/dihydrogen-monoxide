package casa.peterson.dhmo

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.backup.BackupCodec
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * The instrumented flows of TECHNICAL_PLAN §14.1, run against the real app: onboarding to first log; log, edit, delete
 * with Undo; set a day override; change range in Insights; export produces a document.
 */
@RunWith(AndroidJUnit4::class)
class PhaseOneToThreeFlowsTest {
    private val fresh = FreshInstallRule()
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(fresh).around(compose)

    @Test
    fun onboardingLogEditDeleteOverrideInsightsAndExport() {
        completeOnboardingWithManualGoal()

        // First log.
        assertThat(compose.nodes(hasTestTagPrefix("today_entry_"))).isEqualTo(0)
        compose.onNode(hasTestTag("today_list")).performScrollToNode(hasTestTagPrefix("quick_add_"))
        compose.onAllNodes(hasTestTagPrefix("quick_add_")).onFirst().performClick()
        compose.waitForEntry()

        // Edit: open the entry, change the amount, save.
        compose.customAction(hasTestTagPrefix("today_entry_"), "Edit")
        compose.waitForText("Edit entry")
        compose.onNode(hasSetTextAction() and hasText("Amount (ml)")).performTextReplacement("321")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(TIMEOUT) { compose.has(hasText("Edit entry")).not() }
        compose.onNode(hasTestTag("today_list")).performScrollToNode(hasText("321", substring = true))

        // Delete through the accessibility action, then Undo from the snackbar.
        compose.customAction(hasTestTagPrefix("today_entry_"), "Delete")
        compose.waitForText("Undo")
        compose.onNodeWithText("Undo").performClick()
        compose.waitForEntry()

        // Day override from the goal chip.
        compose.onNode(hasTestTag("today_list")).performScrollToIndex(0)
        compose.onNode(hasContentDescription("Daily goal", substring = true)).performClick()
        compose.waitForText("Set today’s goal")
        compose.onNodeWithText("Set today’s goal").performClick()
        compose.onNode(hasSetTextAction() and hasText("Goal (ml)")).performTextReplacement("3000")
        compose.onNodeWithText("Save").performClick()
        compose.waitForText("Today’s goal is set to", substring = true)
        compose.activity.onBackPressedDispatcher.let { dispatcher -> compose.runOnUiThread { dispatcher.onBackPressed() } }
        compose.onNode(hasTestTag("today_list")).performScrollToNode(hasText("Custom goal active"))

        // Insights: switch ranges.
        compose.onNodeWithText("Insights").performClick()
        compose.waitForText("Week")
        compose.onNodeWithText("Month").performClick()
        compose.onNodeWithText("Year").performClick()
        compose.onNodeWithText("Day").performClick()
        compose.waitForText("Through the day")

        // Export: the screen is reachable and the JSON export document holds the entry.
        compose.onNodeWithText("Settings").performClick()
        compose.waitForText("Export")
        compose.onNodeWithText("Export").performClick()
        compose.waitForText("Every drink")
        val doc = runBlocking { fresh.hooks.backup().exportDocument() }
        assertThat(doc.counts.intakeEntries).isEqualTo(1)
        assertThat(doc.entities.intakeEntries.single().volumeMl).isEqualTo(321)
        assertThat(doc.entities.dayOverrides.single().targetMl).isEqualTo(3000)
        assertThat(BackupCodec.decode(BackupCodec.encodeJson(doc, pretty = true))).isEqualTo(doc)
    }

    /** Walks onboarding along the "set my own goal" path, skipping notifications, whatever pages appear. */
    private fun completeOnboardingWithManualGoal() {
        compose.waitForText("Welcome to Dihydrogen Monoxide")
        repeat(MAX_ONBOARDING_STEPS) {
            when {
                compose.has(hasText("Today")) && compose.has(hasText("Quick add")) -> return

                compose.has(hasText("Start tracking")) -> {
                    compose.onNodeWithText("Start tracking").performClick()
                    // Finishing is asynchronous; the button lingers until Today replaces it, so stop looping here.
                    compose.waitForText("Quick add")
                    return
                }

                compose.has(hasText("Not now")) -> compose.onNodeWithText("Not now").performClick()

                // en-US devices default to fluid ounces; the assertions below use millilitres.
                compose.has(hasText("Units and day start")) -> {
                    compose.onNodeWithText("Millilitres").performClick()
                    compose.onNodeWithText("Continue").performClick()
                }

                compose.has(hasText("Choose your goal")) -> {
                    compose.onNodeWithText("I’ll set my own goal").performClick()
                    compose.onNodeWithText("Continue").performClick()
                }

                compose.has(hasSetTextAction() and hasText("Daily goal (ml)")) -> {
                    compose.onNode(hasSetTextAction() and hasText("Daily goal (ml)")).performTextReplacement("2000")
                    compose.onNodeWithText("Continue").performClick()
                }

                else -> compose.onNodeWithText("Continue").performClick()
            }
            compose.waitForIdle()
        }
        compose.waitForText("Quick add")
    }

    private companion object {
        const val TIMEOUT = 10_000L
        const val MAX_ONBOARDING_STEPS = 15
    }
}

private typealias ComposeRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

private fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("testTag starts with $prefix") { node ->
    node.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.startsWith(prefix) == true
}

private fun ComposeRule.nodes(matcher: SemanticsMatcher): Int = onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().size

private fun ComposeRule.has(matcher: SemanticsMatcher): Boolean = onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

/** Waits for [text]; on timeout the failure message carries the semantics tree of the screen. */
private fun ComposeRule.waitForText(text: String, substring: Boolean = false) {
    try {
        waitUntil(10_000) { onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
        throw AssertionError("\"$text\" not shown. Screen:\n" + onRoot().printToString(), e)
    }
}

/** Waits for exactly one drink in Today's list; on timeout the failure message carries the semantics tree. */
private fun ComposeRule.waitForEntry() {
    try {
        waitUntil(10_000) { scrollToEntry() == 1 }
    } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
        throw AssertionError("No single drink shown. Screen:\n" + onRoot().printToString(), e)
    }
}

/** Runs the TalkBack custom action [label] on the first node matching [matcher]. */
private fun ComposeRule.customAction(matcher: SemanticsMatcher, label: String) {
    val node = onAllNodes(matcher).onFirst().fetchSemanticsNode()
    runOnIdle { node.config[SemanticsActions.CustomActions].first { it.label == label }.action() }
}

/** Scrolls Today's list to its drinks and returns how many are shown; 0 when there are none. */
private fun ComposeRule.scrollToEntry(): Int {
    if (has(hasTestTagPrefix("today_entry_")).not()) {
        runCatching { onNode(hasTestTag("today_list")).performScrollToNode(hasTestTagPrefix("today_entry_")) }
    }
    return nodes(hasTestTagPrefix("today_entry_"))
}
