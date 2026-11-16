package casa.peterson.dhmo.core.reminders

import casa.peterson.dhmo.core.data.repository.EntryChangeListener
import casa.peterson.dhmo.core.data.repository.ReminderScheduler
import casa.peterson.dhmo.core.model.EntryChange
import casa.peterson.dhmo.core.model.RescheduleReason
import javax.inject.Inject
import javax.inject.Provider

/**
 * Every entry write recomputes the next reminder (§6.4). The scheduler is a [Provider] because it reads entries
 * through the same `IntakeRepository` that calls this listener.
 */
internal class ReminderEntryListener @Inject constructor(private val scheduler: Provider<ReminderScheduler>) : EntryChangeListener {
    override suspend fun onEntriesChanged(change: EntryChange) = scheduler.get().reschedule(RescheduleReason.ENTRY_CHANGED)
}
