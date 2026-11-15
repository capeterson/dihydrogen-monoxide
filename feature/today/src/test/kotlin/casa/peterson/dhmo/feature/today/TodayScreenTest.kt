package casa.peterson.dhmo.feature.today

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodayScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun quickAddAndSwipeInvokeTheirActions() {
        val entry = TestData.entry(Instant.parse("2026-06-10T08:00:00Z"))
        val container = TestData.defaultContainers(VolumeUnit.ML).first()
        val state = TodayUiState.Ready(
            LocalDate.of(2026, 6, 10), 250, 2000, 2000, GoalMode.CALCULATED, null,
            listOf(container), listOf(entry), VolumeUnit.ML, false, false,
        )
        var added: Int? = null
        var deleted: UUID? = null
        compose.setContent {
            MaterialTheme {
                TodayContent(
                    state, onQuickAdd = { added = it.volumeMl }, onCustomAdd = { _, _ -> }, onDelete = { deleted = it },
                    onEdit = {}, onOpenGoal = {}, onOpenReminders = {}, onOpenHistoryDay = {}, onContainers = {}, onBatteryHelp = {},
                )
            }
        }
        compose.onNodeWithText("250 ml").performClick()
        compose.runOnIdle { assertThat(added).isEqualTo(250) }
        compose.onNodeWithTag("today_entry_${entry.id}").performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { assertThat(deleted).isEqualTo(entry.id) }
    }
}
