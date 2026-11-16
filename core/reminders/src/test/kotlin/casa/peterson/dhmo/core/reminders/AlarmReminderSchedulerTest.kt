package casa.peterson.dhmo.core.reminders

import android.Manifest
import android.app.AlarmManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.model.RescheduleReason
import casa.peterson.dhmo.core.model.settings.ReminderSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class AlarmReminderSchedulerTest {
    @Test
    fun schedulesOneInexactIdleAlarmAtTheNextCandidate() = runTest {
        // R02: 500 ml at 08:30, now 09:00 → next nudge 11:37.
        val env = ReminderTestEnv(at = "09:00")
        env.drink("08:30", 500)
        env.scheduler.reschedule(RescheduleReason.ENTRY_CHANGED)
        val alarm = env.alarms.scheduledAlarms.single()
        assertThat(alarm.triggerAtMs).isEqualTo(env.local("11:37").toEpochMilli())
        assertThat(alarm.getType()).isEqualTo(AlarmManager.RTC_WAKEUP)
        assertThat(alarm.isAllowWhileIdle).isTrue()
        val runtime = env.device.current().reminderRuntime
        assertThat(runtime.scheduledAtMs).isEqualTo(env.local("11:37").toEpochMilli())
        assertThat(runtime.scheduledKind).isEqualTo("NUDGE")
        assertThat(env.rolloverCount).isEqualTo(1)
    }

    @Test
    fun reschedulingReplacesTheAlarm() = runTest {
        val env = ReminderTestEnv(at = "09:00")
        env.drink("08:30", 500)
        env.scheduler.reschedule(RescheduleReason.ENTRY_CHANGED)
        env.clock.set(env.local("09:30"))
        env.drink("09:30", 300)
        env.scheduler.reschedule(RescheduleReason.ENTRY_CHANGED)
        val alarm = env.alarms.scheduledAlarms.single()
        assertThat(alarm.triggerAtMs).isGreaterThan(env.local("11:37").toEpochMilli())
    }

    @Test
    fun aDueReminderIsDeliveredByAnAlarmOneMinuteLaterNeverDirectly() = runTest {
        // R03: behind at 12:00 with the floor passed; reschedule must not post anything itself.
        val env = ReminderTestEnv(at = "12:00")
        env.drink("08:30", 500)
        env.scheduler.reschedule(RescheduleReason.ENTRY_CHANGED)
        assertThat(env.alarms.scheduledAlarms.single().triggerAtMs).isEqualTo(env.local("12:01").toEpochMilli())
        assertThat(env.notifications.allNotifications).isEmpty()
    }

    @Test
    fun rescheduleSnapshotsTodaysGoalFirst() = runTest {
        val env = ReminderTestEnv(at = "09:00")
        env.scheduler.reschedule(RescheduleReason.BOOT)
        assertThat(env.goals.goal(env.day)?.targetMl).isEqualTo(2000)
    }

    @Test
    fun disablingRemindersCancelsTheAlarm() = runTest {
        val env = ReminderTestEnv(at = "09:00")
        env.scheduler.reschedule(RescheduleReason.SETTINGS_CHANGED)
        assertThat(env.alarms.scheduledAlarms).hasSize(1)
        env.settings.update { it.copy(reminders = it.reminders.copy(enabled = false)) }
        env.scheduler.reschedule(RescheduleReason.SETTINGS_CHANGED)
        assertThat(env.alarms.scheduledAlarms).isEmpty()
        assertThat(env.device.current().reminderRuntime.scheduledAtMs).isNull()
    }

    @Test
    fun revokedNotificationPermissionCancelsTheAlarm() = runTest {
        val env = ReminderTestEnv(at = "09:00")
        env.scheduler.reschedule(RescheduleReason.SETTINGS_CHANGED)
        shadowOf(env.app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        env.scheduler.reschedule(RescheduleReason.APP_FOREGROUND)
        assertThat(env.alarms.scheduledAlarms).isEmpty()
    }

    @Test
    fun cancelRemovesTheAlarm() = runTest {
        val env = ReminderTestEnv(at = "09:00")
        env.scheduler.reschedule(RescheduleReason.SETTINGS_CHANGED)
        env.scheduler.cancel()
        assertThat(env.alarms.scheduledAlarms).isEmpty()
    }

    @Test
    fun snoozeDelaysTheNextNudge() = runTest {
        // Behind at 12:00 (R03); snoozing for 30 minutes moves the nudge to 12:30.
        val env = ReminderTestEnv(ReminderSettings(enabled = true, snoozeMinutes = 30), at = "12:00")
        env.drink("08:30", 500)
        env.scheduler.snooze()
        assertThat(env.device.current().reminderRuntime.snoozedUntilMs).isEqualTo(env.local("12:30").toEpochMilli())
        assertThat(env.alarms.scheduledAlarms.single().triggerAtMs).isEqualTo(env.local("12:30").toEpochMilli())
    }

    @Test
    fun afterSleepTheNextAlarmIsTomorrowsWakeRecheck() = runTest {
        val env = ReminderTestEnv(at = "23:30")
        env.scheduler.reschedule(RescheduleReason.TIME_CHANGED)
        assertThat(env.alarms.scheduledAlarms.single().triggerAtMs)
            .isEqualTo(env.local("07:00", env.day.plusDays(1)).toEpochMilli())
        assertThat(env.device.current().reminderRuntime.scheduledKind).isEqualTo("RECHECK")
    }
}
