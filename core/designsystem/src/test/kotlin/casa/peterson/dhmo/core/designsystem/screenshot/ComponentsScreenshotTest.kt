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

/** ProgressRing, VolumeText, GoalChip, QuickAddButton, DayResultColors and HeatmapGrid. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComponentsScreenshotTest {
    @Test
    fun capturesInLightDarkAndLargeFont() {
        captureStable("ProgressRing") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ProgressRing(progress = 0.62f) {
                    VolumeText(ml = 1240, unit = VolumeUnit.ML, style = MaterialTheme.typography.headlineSmall)
                }
                ProgressRing(progress = 1.4f, size = 120.dp, strokeWidth = 12.dp) {
                    Text("140%", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        captureStable("VolumeText") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VolumeText(ml = 1250, unit = VolumeUnit.ML, style = MaterialTheme.typography.headlineMedium)
                VolumeText(ml = 1250, unit = VolumeUnit.L, style = MaterialTheme.typography.headlineMedium)
                VolumeText(ml = 250, unit = VolumeUnit.US_FL_OZ, style = MaterialTheme.typography.headlineMedium)
                VolumeText(ml = 250, unit = VolumeUnit.IMP_FL_OZ, style = MaterialTheme.typography.headlineMedium)
            }
        }
        captureStable("GoalChip") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GoalChip(goalMl = 2400, unit = VolumeUnit.ML)
                GoalChip(goalMl = 2400, unit = VolumeUnit.US_FL_OZ, onClick = {})
            }
        }
        captureStable("QuickAddButton") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickAddButton(amountMl = 150, unit = VolumeUnit.ML, onClick = {})
                    QuickAddButton(amountMl = 250, unit = VolumeUnit.ML, onClick = {})
                }
                QuickAddButton(amountMl = 500, unit = VolumeUnit.US_FL_OZ, onClick = {})
                QuickAddButton(amountMl = 750, unit = VolumeUnit.ML, onClick = {}, enabled = false)
            }
        }
        captureStable("DayResultColors") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DayResult.entries.forEach { DayResultBadge(it) }
            }
        }
        captureStable("HeatmapGrid") { HeatmapSample() }
    }

    @Composable
    private fun HeatmapSample() {
        val cells =
            List(WEEKS * DAYS_PER_WEEK) { index ->
                when {
                    index < 3 -> null
                    index % 11 == 0 -> DayResult.MISSED
                    index % 4 == 0 -> DayResult.PARTLY_MET
                    index > WEEKS * DAYS_PER_WEEK - 4 -> null
                    else -> DayResult.MET
                }
            }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            HeatmapGrid(cells = cells, cellSize = 12.dp)
            HeatmapGrid(cells = cells.take(DAYS_PER_WEEK * 8), cellSize = 20.dp, cellSpacing = 3.dp)
        }
    }

    private companion object {
        const val WEEKS = 26
        const val DAYS_PER_WEEK = 7
    }
}
