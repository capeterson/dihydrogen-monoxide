package casa.peterson.dhmo.widget

import android.app.Application
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import casa.peterson.dhmo.core.common.ApplicationScope
import casa.peterson.dhmo.core.common.suspendRunCatching
import casa.peterson.dhmo.core.data.repository.WidgetUpdater
import casa.peterson.dhmo.core.model.settings.ThemeMode
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Repaints every widget. Enqueued by [WidgetRefreshScheduler]. */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        TodayWidget().updateAll(applicationContext)
        return Result.success()
    }
}

/** The widget's own timed repaints, which nothing else in the app would trigger. */
internal object WidgetRefreshScheduler {
    private const val UNDO_WORK = "widget-undo-expiry"
    private const val DAY_WORK = "widget-day-boundary"

    /** A little after the boundary, so the new day has certainly begun when the widget reads it. */
    private val BOUNDARY_MARGIN: Duration = Duration.ofSeconds(5)
    private val UNDO_MARGIN: Duration = Duration.ofMillis(500)

    /** Takes "Undo" off the widgets once its offer has run out. */
    fun afterUndoWindow(context: Context) = enqueue(context, UNDO_WORK, WidgetUndoStore.UNDO_WINDOW + UNDO_MARGIN)

    /**
     * Repaints at the next hydration-day boundary, so the jar empties for the new day when it starts rather than at the
     * next half-hourly update. Every repaint schedules the next boundary again, replacing the last request.
     */
    fun atNextDay(context: Context, boundary: Instant, now: Instant) =
        enqueue(context, DAY_WORK, Duration.between(now, boundary).coerceAtLeast(Duration.ZERO) + BOUNDARY_MARGIN)

    private fun enqueue(context: Context, name: String, delay: Duration) {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>().setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }
}

/** Ticks when the system's configuration changes in a way the widget draws differently (light and dark, font size). */
internal object WidgetConfiguration {
    private val ticks = MutableStateFlow(0L)
    val changes: Flow<Long> = ticks

    fun changed() {
        ticks.value++
    }
}

/** Night or day, from the app's own theme choice; "Follow system" reads [configuration]. */
internal fun resolveNight(mode: ThemeMode, configuration: Configuration): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
}

/**
 * Repaints the widgets when the system switches between light and dark (for users whose theme follows the system) or
 * changes its font size. The widget's drawings are bitmaps in one scheme and its text sizes are worked out for one font
 * scale, so the launcher cannot adjust them by itself. Works while the app's process is alive; otherwise the next update
 * (at most half an hour later) catches up.
 */
class WidgetConfigurationWatcher @Inject constructor(
    private val widgets: WidgetUpdater,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    fun start(application: Application) {
        var drawn = Drawn.of(application.resources.configuration)
        application.registerComponentCallbacks(
            object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    val now = Drawn.of(newConfig)
                    if (now != drawn) {
                        drawn = now
                        WidgetConfiguration.changed()
                        scope.launch { suspendRunCatching { widgets.updateAll() } }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onLowMemory() = Unit
            },
        )
    }

    /** The parts of the configuration the widget draws differently for. */
    private data class Drawn(val night: Int, val fontScale: Float) {
        companion object {
            fun of(configuration: Configuration) = Drawn(configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK, configuration.fontScale)
        }
    }
}
