package casa.peterson.dhmo.core.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import casa.peterson.dhmo.core.common.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/** Receives the single reminder alarm and hands it to [AlarmReminderScheduler.onAlarm]. Not exported. */
@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: AlarmReminderScheduler

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM) return
        val scheduledAtMs = intent.getLongExtra(EXTRA_SCHEDULED_AT_MS, -1L).takeIf { it >= 0 }
        runAsync(scope) { scheduler.onAlarm(scheduledAtMs) }
    }

    companion object {
        const val ACTION_ALARM = "casa.peterson.dhmo.reminders.ALARM"
        const val EXTRA_SCHEDULED_AT_MS = "scheduledAtMs"
        const val EXTRA_KIND = "kind"
    }
}
