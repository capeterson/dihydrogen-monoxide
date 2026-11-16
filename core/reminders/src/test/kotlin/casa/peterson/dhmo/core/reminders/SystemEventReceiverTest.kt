package casa.peterson.dhmo.core.reminders

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.model.RescheduleReason
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemEventReceiverTest {
    @Test
    fun mapsTheFourSystemBroadcasts() {
        assertThat(SystemEventReceiver.reasonFor(Intent.ACTION_BOOT_COMPLETED)).isEqualTo(RescheduleReason.BOOT)
        assertThat(SystemEventReceiver.reasonFor(Intent.ACTION_TIME_CHANGED)).isEqualTo(RescheduleReason.TIME_CHANGED)
        assertThat(SystemEventReceiver.reasonFor(Intent.ACTION_TIMEZONE_CHANGED)).isEqualTo(RescheduleReason.TIMEZONE_CHANGED)
        assertThat(SystemEventReceiver.reasonFor(Intent.ACTION_MY_PACKAGE_REPLACED)).isEqualTo(RescheduleReason.APP_UPDATED)
    }

    @Test
    fun ignoresAnythingElse() {
        assertThat(SystemEventReceiver.reasonFor(null)).isNull()
        assertThat(SystemEventReceiver.reasonFor("com.example.SPOOF")).isNull()
        assertThat(SystemEventReceiver.reasonFor(Intent.ACTION_LOCKED_BOOT_COMPLETED)).isNull()
    }

    @Test
    fun manifestRegistersTheReceiverForEachAction() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        ).forEach { action ->
            val receivers = context.packageManager.queryBroadcastReceivers(Intent(action).setPackage(context.packageName), 0)
            assertThat(receivers.map { it.activityInfo.name }).contains(SystemEventReceiver::class.java.name)
        }
    }
}
