package casa.peterson.dhmo.core.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import casa.peterson.dhmo.core.common.ApplicationScope
import casa.peterson.dhmo.core.common.suspendRunCatching
import casa.peterson.dhmo.core.data.repository.ReminderScheduler
import casa.peterson.dhmo.core.data.repository.WidgetUpdater
import casa.peterson.dhmo.core.model.RescheduleReason
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/**
 * Reboot, clock, time-zone and app-update broadcasts → `reschedule()` (§8.6), and a widget repaint: each of them can move
 * the hydration day or leave the widget drawn by the previous version. Exported because system broadcasts need it, so
 * any app can send an explicit intent here: anything but the four expected actions is ignored.
 */
@AndroidEntryPoint
class SystemEventReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler

    @Inject lateinit var widgets: WidgetUpdater

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val reason = reasonFor(intent.action) ?: return
        runAsync(scope) {
            scheduler.reschedule(reason)
            suspendRunCatching { widgets.updateAll() }
        }
    }

    companion object {
        fun reasonFor(action: String?): RescheduleReason? = when (action) {
            Intent.ACTION_BOOT_COMPLETED -> RescheduleReason.BOOT
            Intent.ACTION_TIME_CHANGED -> RescheduleReason.TIME_CHANGED
            Intent.ACTION_TIMEZONE_CHANGED -> RescheduleReason.TIMEZONE_CHANGED
            Intent.ACTION_MY_PACKAGE_REPLACED -> RescheduleReason.APP_UPDATED
            else -> null
        }
    }
}
