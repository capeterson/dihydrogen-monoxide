package casa.peterson.dhmo.core.designsystem.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.designsystem.component.DayResult
import casa.peterson.dhmo.core.designsystem.component.DayResultBadge
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.designsystem.component.EmptyState
import casa.peterson.dhmo.core.designsystem.component.GoalChip
import casa.peterson.dhmo.core.designsystem.component.HeatmapGrid
import casa.peterson.dhmo.core.designsystem.component.QuickAddButton
import casa.peterson.dhmo.core.designsystem.component.SectionCard
import casa.peterson.dhmo.core.designsystem.component.SettingsRow
import casa.peterson.dhmo.core.designsystem.component.SwitchRow
import casa.peterson.dhmo.core.designsystem.component.TimePickerRow
import casa.peterson.dhmo.core.designsystem.component.VolumeInputSheetContent
import casa.peterson.dhmo.core.designsystem.component.VolumeText
import casa.peterson.dhmo.core.designsystem.icon.DhmoIcons
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.core.testing.renderScreenVariants
import org.junit.Test
import org.junit.runner.RunWith

/** VolumeInputSheet. Its own class (and JVM): a text field's blinking cursor leaves later captures in the same JVM blank. */
@RunWith(AndroidJUnit4::class)
class VolumeInputSheetRenderTest {
    @Test
    fun rendersInLightAndDarkLargeFont() {
        renderScreenVariants("VolumeInputSheet") {
            VolumeInputSheetContent(title = "Custom amount", initialMl = 330, unit = VolumeUnit.ML, onConfirm = {}, onDismiss = {})
        }
        renderScreenVariants("VolumeInputSheetOunces") {
            VolumeInputSheetContent(
                title = "Custom amount",
                initialMl = 3500,
                unit = VolumeUnit.US_FL_OZ,
                onConfirm = {},
                onDismiss = {},
            )
        }
    }
}
