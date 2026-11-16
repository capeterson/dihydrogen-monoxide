package casa.peterson.dhmo.core.data.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import casa.peterson.dhmo.core.common.ZoneProvider
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.domain.time.hydrationDay
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest

/**
 * Emits the current hydration day and re-emits when the day boundary passes, when the day start setting changes, and
 * on `ACTION_TIME_CHANGED` / `ACTION_TIMEZONE_CHANGED` (a receiver registered only while the flow is collected).
 */
class DayTicker
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
    private val settings: SettingsRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun today(): Flow<LocalDate> = combine(
        settings.settings.map { it.dayStartMinutes }.distinctUntilChanged(),
        systemTimeChanges(),
    ) { dayStartMinutes, _ -> dayStartMinutes }
        .transformLatest { dayStartMinutes ->
            val dayStart = LocalTime.ofSecondOfDay(dayStartMinutes * SECONDS_PER_MINUTE)
            while (true) {
                val now = clock.instant()
                val zone = zoneProvider.current()
                val day = hydrationDay(now, zone.rules.getOffset(now), dayStart)
                emit(day)
                val boundary = day.plusDays(1).atTime(dayStart).atZone(zone).toInstant()
                delay(untilBoundary(now, boundary))
            }
        }
        .distinctUntilChanged()

    private fun untilBoundary(now: Instant, boundary: Instant): Long =
        Duration.between(now, boundary).toMillis().coerceAtLeast(MIN_DELAY_MILLIS)

    private fun systemTimeChanges(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }.onStart { emit(Unit) }

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
        const val MIN_DELAY_MILLIS = 1_000L
    }
}
