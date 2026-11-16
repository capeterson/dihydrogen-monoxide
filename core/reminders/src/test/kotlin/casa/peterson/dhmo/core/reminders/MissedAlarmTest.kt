package casa.peterson.dhmo.core.reminders

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.model.settings.ReminderRuntime
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/** §8.9: a delivery more than 30 minutes after the scheduled time is recorded; the last 10 are kept. */
@RunWith(AndroidJUnit4::class)
class MissedAlarmTest {
    @Test
    fun aLateDeliveryIsRecorded() = runTest {
        val env = ReminderTestEnv(at = "12:00")
        env.scheduler.onAlarm(env.local("11:29").toEpochMilli())
        assertThat(env.device.current().reminderRuntime.missedAlarmTimesMs).containsExactly(env.local("12:00").toEpochMilli())
    }

    @Test
    fun normalDozeLatenessIsNotRecorded() = runTest {
        val env = ReminderTestEnv(at = "12:00")
        env.scheduler.onAlarm(env.local("11:45").toEpochMilli())
        env.scheduler.onAlarm(env.local("11:30").toEpochMilli()) // exactly 30 minutes: not more than the threshold
        assertThat(env.device.current().reminderRuntime.missedAlarmTimesMs).isEmpty()
    }

    @Test
    fun fallsBackToTheStoredScheduleWhenTheExtraIsMissing() = runTest {
        val env = ReminderTestEnv(at = "12:00")
        env.device.update { it.copy(reminderRuntime = ReminderRuntime(scheduledAtMs = env.local("10:00").toEpochMilli())) }
        env.scheduler.onAlarm(null)
        assertThat(env.device.current().reminderRuntime.missedAlarmTimesMs).hasSize(1)
    }

    @Test
    fun keepsOnlyTheLastTen() = runTest {
        val env = ReminderTestEnv(at = "12:00")
        val earlier = (1L..10L).map { env.local("00:00").toEpochMilli() + it }
        env.device.update { it.copy(reminderRuntime = ReminderRuntime(missedAlarmTimesMs = earlier)) }
        env.scheduler.onAlarm(env.local("12:00").minus(Duration.ofHours(2)).toEpochMilli())
        val missed = env.device.current().reminderRuntime.missedAlarmTimesMs
        assertThat(missed).hasSize(AlarmReminderScheduler.MISSED_KEEP)
        assertThat(missed.first()).isEqualTo(earlier[1])
        assertThat(missed.last()).isEqualTo(env.local("12:00").toEpochMilli())
    }
}
