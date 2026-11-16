package casa.peterson.dhmo.core.reminders

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** §8.7 and §8.10: inexact alarms only, so the merged manifest never asks for an exact-alarm permission. */
@RunWith(AndroidJUnit4::class)
class ManifestPermissionsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun requested(): List<String> =
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toList()

    @Test
    fun noExactAlarmPermission() {
        assertThat(requested()).containsNoneOf("android.permission.SCHEDULE_EXACT_ALARM", "android.permission.USE_EXACT_ALARM")
    }

    @Test
    fun declaresTheReminderPermissions() {
        assertThat(requested()).containsAtLeast(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.RECEIVE_BOOT_COMPLETED,
            Manifest.permission.READ_CALENDAR,
        )
    }

    @Test
    fun onlyTheSystemEventReceiverIsExported() {
        fun exported(receiver: Class<*>) = context.packageManager.getReceiverInfo(ComponentName(context, receiver), 0).exported
        assertThat(exported(ReminderAlarmReceiver::class.java)).isFalse()
        assertThat(exported(ReminderActionReceiver::class.java)).isFalse()
        assertThat(exported(SystemEventReceiver::class.java)).isTrue()
    }
}
