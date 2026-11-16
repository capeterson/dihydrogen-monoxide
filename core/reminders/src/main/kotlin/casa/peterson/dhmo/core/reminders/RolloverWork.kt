package casa.peterson.dhmo.core.reminders

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import casa.peterson.dhmo.core.data.repository.ReminderScheduler
import casa.peterson.dhmo.core.data.work.DayRolloverCoordinator
import casa.peterson.dhmo.core.model.RescheduleReason
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/** Enqueues the [DayRolloverWorker] safety net once per process. */
fun interface RolloverWorkScheduler {
    fun ensureScheduled()
}

internal class WorkManagerRolloverScheduler @Inject constructor(@ApplicationContext private val context: Context) : RolloverWorkScheduler {
    private val enqueued = AtomicBoolean(false)

    override fun ensureScheduled() {
        if (!enqueued.compareAndSet(false, true)) return
        val request = PeriodicWorkRequestBuilder<DayRolloverWorker>(1, TimeUnit.DAYS, 1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(DayRolloverWorker.NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

/** Unique periodic work (24 h, flex 1 h) as a safety net (§8.6): backfill and snapshot the goal, then reschedule. */
@HiltWorker
class DayRolloverWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val rollover: DayRolloverCoordinator,
    private val scheduler: ReminderScheduler,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        rollover.refresh()
        scheduler.reschedule(RescheduleReason.GOAL_CHANGED)
        return Result.success()
    }

    companion object {
        const val NAME = "day-rollover"
    }
}
