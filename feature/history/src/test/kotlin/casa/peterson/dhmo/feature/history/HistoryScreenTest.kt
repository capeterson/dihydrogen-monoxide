package casa.peterson.dhmo.feature.history

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.designsystem.theme.DhmoTheme
import casa.peterson.dhmo.core.model.Source
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun readOnlyImportShowsSourceAndAdoptionButNotEditOrDelete() {
        var adopted = false
        val entry = TestData.entry(Instant.parse("2026-03-03T08:00:00Z"), source = Source.HEALTH_CONNECT_IMPORT)
        compose.setContent {
            DhmoTheme(dynamicColor = false) {
                HistoryEntryRow(entry, VolumeUnit.ML, "Other Water", {}, {}, { adopted = true })
            }
        }
        compose.onNodeWithText("Imported from Other Water").assertExists()
        compose.onNodeWithText("Read-only import").assertExists()
        compose.onNodeWithContentDescription("Edit drink").assertDoesNotExist()
        compose.onNodeWithContentDescription("Delete drink").assertDoesNotExist()
        compose.onNodeWithText("Make editable").performClick()
        assertThat(adopted).isTrue()
    }

    @Test
    fun editableEntryInvokesEditAndDelete() {
        var edited = false
        var deleted = false
        val entry = TestData.entry(Instant.parse("2026-03-03T08:00:00Z"))
        compose.setContent {
            DhmoTheme(dynamicColor = false) {
                HistoryEntryRow(entry, VolumeUnit.ML, null, { edited = true }, { deleted = true }, {})
            }
        }
        compose.onNodeWithContentDescription("Edit drink").performClick()
        compose.onNodeWithContentDescription("Delete drink").performClick()
        assertThat(edited).isTrue()
        assertThat(deleted).isTrue()
        compose.onNodeWithText("Make editable").assertDoesNotExist()
    }

    @Test
    fun unloggedDayDoesNotShowAMissedGoal() {
        compose.setContent {
            DhmoTheme(dynamicColor = false) {
                HistoryDayHeader(HistoryItem.DayHeader(LocalDate.of(2026, 3, 3), 0, 2000, false, 0), VolumeUnit.ML)
            }
        }
        compose.onNodeWithContentDescription("Goal missed").assertDoesNotExist()
    }
}
