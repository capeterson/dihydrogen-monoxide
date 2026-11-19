package casa.peterson.dhmo.feature.reminders

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.model.settings.ReminderSettings
import casa.peterson.dhmo.core.testing.renderScreenVariants
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemindersRenderTest {
    @Test
    fun rendersReminderSettings() = renderScreenVariants("Reminders") {
        RemindersContent(
            state = RemindersUiState(settings = ReminderSettings(enabled = true)),
            notificationsAllowed = true,
            calendarDenied = false,
            onBack = {},
            onOpenCurve = {},
            onEnabled = {},
            onNotificationSettings = {},
            onWake = {},
            onSleep = {},
            onInterval = {},
            onTolerance = {},
            onEscalation = {},
            onSummary = {},
            onCalendar = {},
        )
    }
}
