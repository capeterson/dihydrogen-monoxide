package casa.peterson.dhmo.core.reminders

import casa.peterson.dhmo.core.data.repository.ReminderScheduler
import casa.peterson.dhmo.core.model.RescheduleReason
import javax.inject.Inject

/** Replaced by `AlarmReminderScheduler` in T13. */
internal class NoOpReminderScheduler
@Inject
constructor() : ReminderScheduler {
    override suspend fun reschedule(reason: RescheduleReason) = Unit

    override suspend fun cancel() = Unit

    override suspend fun snooze() = Unit
}
