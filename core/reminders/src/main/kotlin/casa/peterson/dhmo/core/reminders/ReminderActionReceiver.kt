package casa.peterson.dhmo.core.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import casa.peterson.dhmo.core.common.ApplicationScope
import casa.peterson.dhmo.core.data.repository.ContainerRepository
import casa.peterson.dhmo.core.data.repository.ReminderScheduler
import casa.peterson.dhmo.core.data.usecase.intake.LogIntakeUseCase
import casa.peterson.dhmo.core.model.Source
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/** The reminder notification's actions: log one of the top two containers, or snooze (§8.8). Not exported. */
@AndroidEntryPoint
class ReminderActionReceiver : BroadcastReceiver() {
    @Inject lateinit var handler: ReminderActionHandler

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val containerId = intent.getStringExtra(EXTRA_CONTAINER_ID)
        if (action != ACTION_LOG && action != ACTION_SNOOZE) return
        runAsync(scope) { handler.handle(action, containerId) }
    }

    companion object {
        const val ACTION_LOG = "casa.peterson.dhmo.reminders.LOG"
        const val ACTION_SNOOZE = "casa.peterson.dhmo.reminders.SNOOZE"
        const val EXTRA_CONTAINER_ID = "containerId"

        fun logIntent(context: Context, containerId: UUID): Intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(ACTION_LOG)
            .putExtra(EXTRA_CONTAINER_ID, containerId.toString())

        fun snoozeIntent(context: Context): Intent = Intent(context, ReminderActionReceiver::class.java).setAction(ACTION_SNOOZE)
    }
}

/** The logic behind [ReminderActionReceiver], separate so it can be tested without Hilt. */
class ReminderActionHandler @Inject constructor(
    private val containers: ContainerRepository,
    private val logIntake: LogIntakeUseCase,
    private val scheduler: ReminderScheduler,
    private val notifier: ReminderNotifier,
) {
    suspend fun handle(action: String, containerId: String?) {
        when (action) {
            ReminderActionReceiver.ACTION_LOG -> {
                notifier.cancelReminder()
                val id = containerId?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return
                val container = containers.getAll().firstOrNull { it.id == id } ?: return
                // The entry listeners then update the widget, the Health Connect queue and the next reminder.
                logIntake(container.volumeMl, Source.NOTIFICATION)
            }

            ReminderActionReceiver.ACTION_SNOOZE -> scheduler.snooze()
        }
    }
}
