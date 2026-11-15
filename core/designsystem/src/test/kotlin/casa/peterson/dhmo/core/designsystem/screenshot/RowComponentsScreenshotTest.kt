package casa.peterson.dhmo.core.designsystem.screenshot

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
import casa.peterson.dhmo.core.designsystem.component.ProgressRing
import casa.peterson.dhmo.core.designsystem.component.QuickAddButton
import casa.peterson.dhmo.core.designsystem.component.SectionCard
import casa.peterson.dhmo.core.designsystem.component.SettingsRow
import casa.peterson.dhmo.core.designsystem.component.SwitchRow
import casa.peterson.dhmo.core.designsystem.component.TimePickerRow
import casa.peterson.dhmo.core.designsystem.component.VolumeInputSheetContent
import casa.peterson.dhmo.core.designsystem.component.VolumeText
import casa.peterson.dhmo.core.designsystem.icon.DhmoIcons
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** TimePickerRow, SettingsRow and SwitchRow. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RowComponentsScreenshotTest {
    @Test
    fun capturesInLightDarkAndLargeFont() {
        captureStable("TimePickerRow") {
            Column(Modifier.fillMaxWidth()) {
                TimePickerRow(label = "Wake up", minutesOfDay = 7 * 60 + 30, onTimeSelected = {})
                TimePickerRow(label = "Go to bed", minutesOfDay = 22 * 60 + 45, onTimeSelected = {})
            }
        }
        captureStable("SettingsRow") {
            Column(Modifier.fillMaxWidth()) {
                SettingsRow(title = "Units", summary = "Millilitres", leadingIcon = DhmoIcons.WaterDrop, onClick = {})
                SettingsRow(title = "Version", summary = "1.0.0")
                SwitchRow(
                    title = "Reminders",
                    summary = "Nudge me when I am behind",
                    checked = true,
                    onCheckedChange = {},
                    leadingIcon = DhmoIcons.Notifications,
                )
                SwitchRow(title = "Weather adjustment", checked = false, onCheckedChange = {})
                SwitchRow(title = "Unavailable option", checked = false, onCheckedChange = {}, enabled = false)
            }
        }
    }
}
