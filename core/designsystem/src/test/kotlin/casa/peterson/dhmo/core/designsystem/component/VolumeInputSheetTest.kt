package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.designsystem.theme.DhmoTheme
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VolumeInputSheetTest {
    @get:Rule val composeRule = createComposeRule()

    private var confirmed: Int? = null
    private var dismissed = false

    private fun show(unit: VolumeUnit, initialMl: Int = 250) {
        composeRule.setContent {
            DhmoTheme(dynamicColor = false) {
                VolumeInputSheetContent(
                    title = "Add water",
                    initialMl = initialMl,
                    unit = unit,
                    onConfirm = { confirmed = it },
                    onDismiss = { dismissed = true },
                    confirmLabel = "Add",
                )
            }
        }
    }

    private val field get() = composeRule.onNode(hasSetTextAction())

    @Test
    fun startsWithTheInitialAmountInMillilitres() {
        show(VolumeUnit.ML)
        field.assertTextEquals("Amount in ml", "250", includeEditableText = true)
    }

    @Test
    fun typedAmountIsConfirmedInMillilitres() {
        show(VolumeUnit.ML)
        field.performTextClearance()
        field.performTextInput("330")
        composeRule.onNodeWithText("Add").performClick()
        assertThat(confirmed).isEqualTo(330)
    }

    @Test
    fun ouncesAreConvertedToMillilitres() {
        show(VolumeUnit.US_FL_OZ)
        field.performTextClearance()
        field.performTextInput("8.5")
        composeRule.onNodeWithText("Add").performClick()
        assertThat(confirmed).isEqualTo(251) // 8.5 x 29.5735 = 251.37
    }

    @Test
    fun acceptsACommaAsTheDecimalMark() {
        show(VolumeUnit.L)
        field.performTextClearance()
        field.performTextInput("1,5")
        composeRule.onNodeWithText("Add").performClick()
        assertThat(confirmed).isEqualTo(1500)
    }

    @Test
    fun initialAmountIsShownInTheChosenUnit() {
        show(VolumeUnit.L, initialMl = 1250)
        field.assertTextEquals("Amount in L", "1.25", includeEditableText = true)
    }

    @Test
    fun outOfRangeAmountDisablesConfirmAndShowsAnError() {
        show(VolumeUnit.ML)
        field.performTextClearance()
        field.performTextInput("5000")
        composeRule.onNodeWithText("Add").assertIsNotEnabled()
        composeRule.onNodeWithText("Enter an amount from 10 ml to 3,000 ml").assertExists()
    }

    @Test
    fun emptyFieldDisablesConfirm() {
        show(VolumeUnit.ML)
        field.performTextClearance()
        composeRule.onNodeWithText("Add").assertIsNotEnabled()
        field.performTextInput("100")
        composeRule.onNodeWithText("Add").assertIsEnabled()
    }

    @Test
    fun sliderUpdatesTheFieldAndSnapsToTheStep() {
        show(VolumeUnit.ML)
        composeRule.onNode(hasContentDescription("Amount slider")).performSemanticsAction(SemanticsActions.SetProgress) { it(504f) }
        field.assertTextEquals("Amount in ml", "500", includeEditableText = true)
        composeRule.onNodeWithText("Add").performClick()
        assertThat(confirmed).isEqualTo(500)
    }

    @Test
    fun cancelDismisses() {
        show(VolumeUnit.ML)
        composeRule.onNodeWithText("Cancel").performClick()
        assertThat(dismissed).isTrue()
        assertThat(confirmed).isNull()
    }

    @Test
    fun fieldTextRoundTrips() {
        assertThat(volumeFieldText(250, VolumeUnit.ML, Locale.US)).isEqualTo("250")
        assertThat(volumeFieldText(1250, VolumeUnit.L, Locale.US)).isEqualTo("1.25")
        assertThat(volumeFieldText(1250, VolumeUnit.L, Locale.GERMANY)).isEqualTo("1,25")
        assertThat(parseVolumeField("1,25", VolumeUnit.L)).isEqualTo(1250)
        assertThat(parseVolumeField("", VolumeUnit.ML)).isNull()
        assertThat(parseVolumeField(".", VolumeUnit.ML)).isNull()
    }
}
