package casa.peterson.dhmo.sync.healthconnect.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import casa.peterson.dhmo.sync.healthconnect.HealthConnectControllerImpl
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

internal class HealthConnectWorkScheduler @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val work get() = WorkManager.getInstance(context)

    /** Replaces any waiting run, so a burst of changes becomes one sync after [delaySeconds] (a debounce). */
    fun enqueueSync(delaySeconds: Long) {
        val request = OneTimeWorkRequestBuilder<HealthConnectSyncWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        work.enqueueUniqueWork(SYNC_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<HealthConnectPeriodicWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        work.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelPeriodic() {
        work.cancelUniqueWork(PERIODIC_NAME)
    }

    fun cancelAll() {
        work.cancelUniqueWork(SYNC_NAME)
        work.cancelUniqueWork(PERIODIC_NAME)
    }

    companion object {
        const val SYNC_NAME = "hc-sync"
        const val PERIODIC_NAME = "hc-periodic"
        private const val PERIOD_MINUTES = 15L
        private const val BACKOFF_SECONDS = 30L
    }
}

/** What a worker should report after a run. */
internal enum class HcRunResult { SUCCESS, RETRY, FAILURE }

internal fun HcRunResult.toWorkResult(): androidx.work.ListenableWorker.Result = when (this) {
    HcRunResult.SUCCESS -> androidx.work.ListenableWorker.Result.success()
    HcRunResult.RETRY -> androidx.work.ListenableWorker.Result.retry()
    HcRunResult.FAILURE -> androidx.work.ListenableWorker.Result.failure()
}

@HiltWorker
internal class HealthConnectSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val controller: HealthConnectControllerImpl,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = controller.runSync().toWorkResult()
}

@HiltWorker
internal class HealthConnectPeriodicWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val controller: HealthConnectControllerImpl,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = controller.runSync().toWorkResult()
}
