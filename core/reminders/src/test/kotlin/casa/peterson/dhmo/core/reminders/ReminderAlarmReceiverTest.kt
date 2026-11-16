package casa.peterson.dhmo.core.reminders

import android.app.Notification
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.model.settings.ReminderRuntime
import casa.peterson.dhmo.core.model.settings.ReminderSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** The alarm path ([AlarmReminderScheduler.onAlarm]) that [ReminderAlarmReceiver] runs, against rows of §8.5. */
@RunWith(AndroidJUnit4::class)
class ReminderAlarmReceiverTest {
    @Test
    fun postsANudgeAndSchedulesTheNextOne() = runTest {
        // R03: 12:00, 500 of 2,000 ml, last drink 08:30 → NUDGE now, next 13:00; deficit 252 ml.
        val env = ReminderTestEnv(at = "12:00")
        env.drink("08:30", 500)
        env.scheduler.onAlarm(env.local("12:00").toEpochMilli())
        val posted = env.notifications.getNotification(NotificationIds.NOTIF_REMINDER)
        assertThat(posted).isNotNull()
        assertThat(posted.extras.getString(Notification.EXTRA_TEXT)).contains("252")
        assertThat(posted.channelId).isEqualTo(ReminderNotifier.CHANNEL_REMINDERS)
        assertThat(env.device.current().reminderRuntime.lastReminderAtMs).isEqualTo(env.local("12:00").toEpochMilli())
        assertThat(env.alarms.scheduledAlarms.single().triggerAtMs).isEqualTo(env.local("13:00").toEpochMilli())
    }

    @Test
    fun postsAnEscalationOncePerDay() = runTest {
        // R10: escalation on; 21:10, 900 of 2,000, last drink 16:00, last reminder 20:05 → ESCALATION, next 22:10.
        val env = ReminderTestEnv(ReminderSettings(enabled = true, escalationEnabled = true), at = "21:10")
        env.drink("16:00", 900)
        env.device.update { it.copy(reminderRuntime = ReminderRuntime(lastReminderAtMs = env.local("20:05").toEpochMilli())) }
        env.scheduler.onAlarm(env.local("21:10").toEpochMilli())
        val posted = env.notifications.getNotification(NotificationIds.NOTIF_REMINDER)
        assertThat(posted.extras.getString(Notification.EXTRA_TEXT)).contains("1,100")
        assertThat(env.device.current().reminderRuntime.escalationSentDay).isEqualTo(env.day.toEpochDay())
        assertThat(env.alarms.scheduledAlarms.single().triggerAtMs).isEqualTo(env.local("22:10").toEpochMilli())
    }

    @Test
    fun postsTheEndOfDaySummary() = runTest {
        // R13: summary on; 22:30, 1,500 of 2,000 → SUMMARY, next is tomorrow's wake.
        val env = ReminderTestEnv(ReminderSettings(enabled = true, endOfDaySummaryEnabled = true), at = "22:30")
        env.drink("20:00", 1500)
        env.device.update { it.copy(reminderRuntime = ReminderRuntime(lastReminderAtMs = env.local("21:00").toEpochMilli())) }
        env.scheduler.onAlarm(env.local("22:30").toEpochMilli())
        val summary = env.notifications.getNotification(NotificationIds.NOTIF_SUMMARY)
        assertThat(summary.channelId).isEqualTo(ReminderNotifier.CHANNEL_SUMMARIES)
        assertThat(env.notifications.getNotification(NotificationIds.NOTIF_REMINDER)).isNull()
        assertThat(env.device.current().reminderRuntime.summarySentDay).isEqualTo(env.day.toEpochDay())
        assertThat(env.alarms.scheduledAlarms.single().triggerAtMs).isEqualTo(env.local("07:00", env.day.plusDays(1)).toEpochMilli())
    }

    @Test
    fun postsNothingWhenOnPace() = runTest {
        // R02: on pace at 09:00 → nothing now, next 11:37.
        val env = ReminderTestEnv(at = "09:00")
        env.drink("08:30", 500)
        env.scheduler.onAlarm(env.local("09:00").toEpochMilli())
        assertThat(env.notifications.allNotifications).isEmpty()
        assertThat(env.alarms.scheduledAlarms.single().triggerAtMs).isEqualTo(env.local("11:37").toEpochMilli())
    }

    @Test
    fun clearsAnExpiredSnooze() = runTest {
        val env = ReminderTestEnv(at = "12:00")
        env.device.update { it.copy(reminderRuntime = ReminderRuntime(snoozedUntilMs = env.local("11:30").toEpochMilli())) }
        env.scheduler.onAlarm(env.local("12:00").toEpochMilli())
        assertThat(env.device.current().reminderRuntime.snoozedUntilMs).isNull()
    }

    @Test
    fun aDisabledEngineCancelsInsteadOfPosting() = runTest {
        val env = ReminderTestEnv(ReminderSettings(enabled = false), at = "12:00")
        env.drink("08:30", 500)
        env.scheduler.onAlarm(env.local("12:00").toEpochMilli())
        assertThat(env.notifications.allNotifications).isEmpty()
        assertThat(env.alarms.scheduledAlarms).isEmpty()
    }

    @Test
    fun theReminderNotificationIsAutoCancelReminderCategory() = runTest {
        val env = ReminderTestEnv(at = "12:00")
        env.drink("08:30", 500)
        env.scheduler.onAlarm(null)
        val posted = env.notifications.getNotification(NotificationIds.NOTIF_REMINDER)
        assertThat(posted.category).isEqualTo(Notification.CATEGORY_REMINDER)
        assertThat(posted.flags and Notification.FLAG_AUTO_CANCEL).isNotEqualTo(0)
        assertThat(shadowOf(posted.contentIntent).savedIntent.dataString).isEqualTo(ReminderNotifier.TODAY_DEEP_LINK)
    }
}
