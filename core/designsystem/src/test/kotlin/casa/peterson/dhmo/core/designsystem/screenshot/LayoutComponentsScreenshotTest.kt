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

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DhmoTopBarScreenshotTest {
    @Test
    fun capturesInLightDarkAndLargeFont() {
        captureStable("DhmoTopBar") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DhmoTopBar(title = "History")
                DhmoTopBar(
                    title = "Reminders and notifications",
                    onNavigateBack = {},
                    actions = {
                        IconButton(onClick = {}) {
                            Icon(painterResource(DhmoIcons.Settings), contentDescription = "Settings")
                        }
                    },
                )
            }
        }
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SectionCardScreenshotTest {
    @Test
    fun capturesInLightDarkAndLargeFont() {
        captureStable("SectionCard") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionCard(title = "This week") {
                    Text("Average 2,100 ml per day", style = MaterialTheme.typography.bodyLarge)
                }
                SectionCard { Text("A card without a title", style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EmptyStateScreenshotTest {
    @Test
    fun capturesInLightDarkAndLargeFont() {
        captureStable("EmptyState") {
            EmptyState(
                title = "No drinks yet",
                message = "Log your first drink and it will show up here.",
                actionLabel = "Add a drink",
                onAction = {},
            )
        }
    }
}
