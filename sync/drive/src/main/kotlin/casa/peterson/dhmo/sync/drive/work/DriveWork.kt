package casa.peterson.dhmo.sync.drive.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import casa.peterson.dhmo.core.model.settings.BackupFrequency
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

internal interface DriveScheduler {
    fun schedule(frequency: BackupFrequency, unmeteredOnly: Boolean)

    fun cancel()

    fun enqueueNow()
}

internal class DriveWorkScheduler @Inject constructor(@param:ApplicationContext private val context: Context) : DriveScheduler {
    private val work get() = WorkManager.getInstance(context)

    /** Replaces the schedule, so a changed frequency or network rule takes effect straight away. */
    override fun schedule(frequency: BackupFrequency, unmeteredOnly: Boolean) {
        val days = if (frequency == BackupFrequency.DAILY) 1L else DAYS_PER_WEEK
        val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(days, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()
        work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override fun cancel() {
        work.cancelUniqueWork(PERIODIC)
        work.cancelUniqueWork(NOW)
    }

    override fun enqueueNow() {
        val request = OneTimeWorkRequestBuilder<DriveBackupWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        work.enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val PERIODIC = "drive-backup"
        const val NOW = "drive-backup-now"
        private const val DAYS_PER_WEEK = 7L
        private const val BACKOFF_MINUTES = 10L
    }
}

@HiltWorker
internal class DriveBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runner: DriveBackupRunner,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (runner.run()) {
        RunOutcome.SUCCESS -> Result.success()
        RunOutcome.RETRY -> Result.retry()
        RunOutcome.FAILURE -> Result.failure()
    }
}
