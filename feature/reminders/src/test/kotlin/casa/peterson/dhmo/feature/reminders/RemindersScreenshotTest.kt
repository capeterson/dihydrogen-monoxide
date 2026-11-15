package casa.peterson.dhmo.feature.reminders

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.model.settings.ReminderSettings
import casa.peterson.dhmo.core.testing.captureScreenVariants
import java.io.File
import javax.imageio.ImageIO
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RemindersScreenshotTest {
    @Test
    fun capturesReminderSettings() {
        var failure: AssertionError? = null
        repeat(4) {
            try {
                captureScreenVariants("Reminders") {
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
                val directory = File(System.getProperty("roborazzi.output.dir") ?: "src/test/screenshots")
                val blank = listOf("light", "dark", "largefont").any { suffix ->
                    val file = File(directory, "Reminders_$suffix.png")
                    file.exists() && ImageIO.read(file).getRGB(0, 0) ushr 24 == 0
                }
                if (!blank) return
                failure = AssertionError("Blank screenshot")
            } catch (error: AssertionError) {
                failure = error
            }
        }
        throw checkNotNull(failure)
    }
}
