package casa.peterson.dhmo.core.data.repository

import casa.peterson.dhmo.core.model.RescheduleReason

/** Implemented by `:core:reminders` (Phase 2). */
interface ReminderScheduler {
    suspend fun reschedule(reason: RescheduleReason)

    suspend fun cancel()

    suspend fun snooze()
}
