package casa.peterson.dhmo.core.reminders

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.model.Source
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderActionReceiverTest {
    @Test
    fun logActionLogsTheContainerFromTheNotification() = runTest {
        val env = ReminderTestEnv(at = "12:00")
        env.drink("08:30", 500)
        env.scheduler.onAlarm(env.local("12:00").toEpochMilli())
        val container = env.containers.getAll()[1]
        env.actions.handle(ReminderActionReceiver.ACTION_LOG, container.id.toString())
        val logged = env.intake.all.last()
        assertThat(logged.volumeMl).isEqualTo(container.volumeMl)
        assertThat(logged.source).isEqualTo(Source.NOTIFICATION)
        assertThat(env.notifications.getNotification(NotificationIds.NOTIF_REMINDER)).isNull()
    }

    @Test
    fun unknownOrMalformedContainersLogNothing() = runTest {
        val env = ReminderTestEnv(at = "12:00")
        env.actions.handle(ReminderActionReceiver.ACTION_LOG, UUID.randomUUID().toString())
        env.actions.handle(ReminderActionReceiver.ACTION_LOG, "not-a-uuid")
        env.actions.handle(ReminderActionReceiver.ACTION_LOG, null)
        assertThat(env.intake.all).isEmpty()
    }

    @Test
    fun snoozeActionSnoozes() = runTest {
        val env = ReminderTestEnv(at = "12:00")
        env.actions.handle(ReminderActionReceiver.ACTION_SNOOZE, null)
        assertThat(env.device.current().reminderRuntime.snoozedUntilMs).isEqualTo(env.local("12:30").toEpochMilli())
    }

    @Test
    fun intentsTargetTheReceiverExplicitly() {
        val env = ReminderTestEnv()
        val id = UUID.randomUUID()
        val log = ReminderActionReceiver.logIntent(env.app, id)
        assertThat(log.component?.className).isEqualTo(ReminderActionReceiver::class.java.name)
        assertThat(log.getStringExtra(ReminderActionReceiver.EXTRA_CONTAINER_ID)).isEqualTo(id.toString())
        assertThat(ReminderActionReceiver.snoozeIntent(env.app).action).isEqualTo(ReminderActionReceiver.ACTION_SNOOZE)
    }
}
