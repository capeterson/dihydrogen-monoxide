package casa.peterson.dhmo.core.reminders

import kotlinx.coroutines.test.runTest
import org.junit.Test

class NoOpReminderSchedulerTest {
    @Test
    fun `placeholder scheduler and notifier do nothing and do not throw`() = runTest {
        val scheduler = NoOpReminderScheduler()
        scheduler.reschedule(casa.peterson.dhmo.core.model.RescheduleReason.BOOT)
        scheduler.snooze()
        scheduler.cancel()
        ReminderNotifier().ensureChannels()
    }
}
