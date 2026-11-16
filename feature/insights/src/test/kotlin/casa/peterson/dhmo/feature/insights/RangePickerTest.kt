package casa.peterson.dhmo.feature.insights

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.designsystem.theme.DhmoTheme
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RangePickerTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 3, 16)
    private var applied: Triple<LocalDate, LocalDate, Boolean>? = null

    private fun show(from: LocalDate, to: LocalDate, compare: Boolean = false) {
        compose.setContent {
            DhmoTheme(dynamicColor = false) {
                RangePickerDialog(from, to, compare, today, onApply = { f, t, c -> applied = Triple(f, t, c) }, onDismiss = {})
            }
        }
    }

    @Test
    fun presetThenApply() {
        show(today.minusDays(29), today)
        compose.onNodeWithText("Last 7 days").performClick()
        compose.onNodeWithTag(TAG_APPLY).performClick()
        assertThat(applied).isEqualTo(Triple(today.minusDays(6), today, false))
    }

    @Test
    fun compareSwitchIsPassedOn() {
        show(today.minusDays(13), today)
        compose.onNodeWithText("Compare with the previous period").performClick()
        compose.onNodeWithTag(TAG_APPLY).performClick()
        assertThat(applied).isEqualTo(Triple(today.minusDays(13), today, true))
    }

    @Test
    fun invalidRangesCannotBeApplied() {
        show(today, today.minusDays(3))
        compose.onNodeWithText("The start must be on or before the end.").assertExists()
        compose.onNodeWithTag(TAG_APPLY).assertIsNotEnabled()
        compose.onNodeWithText("Last 30 days").performClick()
        compose.onNodeWithTag(TAG_APPLY).assertIsEnabled()
    }

    @Test
    fun validation() {
        assertThat(rangeError(today, today, today)).isNull()
        assertThat(rangeError(today, today.plusDays(1), today)).isEqualTo(R.string.feature_insights_picker_error_future)
        assertThat(rangeError(today.minusDays(366), today, today)).isEqualTo(R.string.feature_insights_picker_error_length)
        assertThat(rangeError(today.minusDays(365), today, today)).isNull()
    }
}
