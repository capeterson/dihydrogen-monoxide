package casa.peterson.dhmo.core.weather

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import casa.peterson.dhmo.core.data.repository.WeatherController
import casa.peterson.dhmo.core.data.repository.WeatherFailure
import casa.peterson.dhmo.core.data.repository.WeatherResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

internal interface WeatherWorkScheduler {
    fun ensureScheduled()

    fun cancel()
}

internal class WorkManagerWeatherScheduler @Inject constructor(@ApplicationContext private val context: Context) : WeatherWorkScheduler {
    override fun ensureScheduled() {
        val request = PeriodicWorkRequestBuilder<WeatherWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WeatherWorker.NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WeatherWorker.NAME)
    }
}

/** Daily fetch. Network and HTTP failures retry with backoff; "no data" (no location yet, bad payload) does not. */
@HiltWorker
internal class WeatherWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val controller: WeatherController,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (val result = controller.refreshNow()) {
        is WeatherResult.Success -> Result.success()

        is WeatherResult.Failure -> when (result.reason) {
            WeatherFailure.NETWORK, WeatherFailure.HTTP -> Result.retry()
            WeatherFailure.PARSE, WeatherFailure.NO_DATA -> Result.success()
        }
    }

    companion object {
        const val NAME = "weather-daily"
    }
}
