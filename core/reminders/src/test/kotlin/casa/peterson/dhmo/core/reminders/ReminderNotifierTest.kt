package casa.peterson.dhmo.core.reminders

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class ReminderNotifierTest {
    @Test
    fun createsTheThreeChannels() {
        val env = ReminderTestEnv()
        val manager = env.app.getSystemService(NotificationManager::class.java)
        assertThat(manager.getNotificationChannel(ReminderNotifier.CHANNEL_REMINDERS).importance)
            .isEqualTo(NotificationManager.IMPORTANCE_DEFAULT)
        assertThat(manager.getNotificationChannel(ReminderNotifier.CHANNEL_SUMMARIES).importance)
            .isEqualTo(NotificationManager.IMPORTANCE_LOW)
        assertThat(manager.getNotificationChannel(ReminderNotifier.CHANNEL_BACKUP).importance)
            .isEqualTo(NotificationManager.IMPORTANCE_MIN)
        for ((id, sound) in listOf(
            ReminderNotifier.CHANNEL_REMINDERS to "reminder_chime",
            ReminderNotifier.CHANNEL_SUMMARIES to "summary_chime",
            ReminderNotifier.CHANNEL_BACKUP to "backup_chime",
        )) {
            assertThat(manager.getNotificationChannel(id).sound.toString())
                .isEqualTo("android.resource://${env.app.packageName}/raw/$sound")
        }
    }

    @Test
    fun migratesLegacyChannelAndPreservesImportanceAndMute() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel("reminders", "Old reminders", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
            },
        )
        val notifier = ReminderNotifier(app)
        notifier.ensureChannels()
        val migrated = manager.getNotificationChannel(ReminderNotifier.CHANNEL_REMINDERS)
        assertThat(migrated.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
        assertThat(migrated.sound).isNull()
        assertThat(manager.getNotificationChannel("reminders")).isNull()
        notifier.ensureChannels()
        assertThat(manager.getNotificationChannel(ReminderNotifier.CHANNEL_REMINDERS).sound).isNull()
    }

    @Test
    fun replacesLegacyDefaultSound() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel("reminders", "Old reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(Settings.System.DEFAULT_NOTIFICATION_URI, null)
            },
        )
        ReminderNotifier(app).ensureChannels()
        assertThat(manager.getNotificationChannel(ReminderNotifier.CHANNEL_REMINDERS).sound.toString())
            .isEqualTo("android.resource://${app.packageName}/raw/reminder_chime")
    }

    @Test
    fun reminderHasTwoLogActionsSnoozeAndCustom() = runTest {
        val env = ReminderTestEnv()
        val containers = env.containers.getAll()
        env.notifier.showReminder(ReminderMessage.Nudge(250), containers, VolumeUnit.ML, snoozeMinutes = 30)
        val posted = env.notifications.getNotification(NotificationIds.NOTIF_REMINDER)
        val actions = posted.actions.map { it.title.toString() }
        assertThat(actions).hasSize(4)
        assertThat(actions[0]).startsWith("+ ${containers[0].name}")
        assertThat(actions[1]).startsWith("+ ${containers[1].name}")
        assertThat(actions[2]).isEqualTo("Later (30 min)")
        assertThat(actions[3]).isEqualTo("Custom…")
        assertThat(shadowOf(posted.actions[3].actionIntent).savedIntent.action).isEqualTo(ReminderNotifier.ACTION_QUICK_LOG)
        assertThat(shadowOf(posted.actions[0].actionIntent).requestCode).isEqualTo(NotificationIds.RC_ACTION_LOG_1)
    }

    @Test
    fun aNewReminderReplacesTheOldOne() = runTest {
        val env = ReminderTestEnv()
        env.notifier.showReminder(ReminderMessage.Nudge(250), emptyList(), VolumeUnit.ML, 30)
        env.notifier.showReminder(ReminderMessage.Escalation(850), emptyList(), VolumeUnit.ML, 30)
        assertThat(env.notifications.allNotifications).hasSize(1)
        assertThat(env.notifications.getNotification(NotificationIds.NOTIF_REMINDER).extras.getString(Notification.EXTRA_TEXT))
            .contains("850\u00a0ml")
    }

    @Test
    fun summaryUsesFluidOuncesWhenChosen() {
        val env = ReminderTestEnv()
        env.notifier.showSummary(consumedMl = 2000, goalMl = 2000, units = VolumeUnit.US_FL_OZ)
        val text = env.notifications.getNotification(NotificationIds.NOTIF_SUMMARY).extras.getString(Notification.EXTRA_TEXT)
        assertThat(text).contains("fl oz")
    }
}
