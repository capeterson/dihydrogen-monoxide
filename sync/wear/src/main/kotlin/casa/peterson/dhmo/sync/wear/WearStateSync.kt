package casa.peterson.dhmo.sync.wear

import android.content.Context
import android.util.Log
import casa.peterson.dhmo.core.common.ApplicationScope
import casa.peterson.dhmo.core.data.repository.ContainerRepository
import casa.peterson.dhmo.core.data.repository.GoalRepository
import casa.peterson.dhmo.core.data.repository.IntakeRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.time.DayTicker
import casa.peterson.dhmo.core.model.Container
import casa.peterson.dhmo.core.model.DailyGoal
import casa.peterson.dhmo.core.model.DailyTotal
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.core.wear.WearContainer
import casa.peterson.dhmo.core.wear.WearProtocol
import casa.peterson.dhmo.core.wear.WearState
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Today's state for the watch: total, effective goal, display unit and the first [WearState.MAX_TILE_BUTTONS] containers. */
fun buildWearState(day: LocalDate, total: DailyTotal?, goal: DailyGoal?, units: VolumeUnit, containers: List<Container>, nowMillis: Long) =
    WearState(
        day = day.toString(),
        totalMl = total?.totalMl ?: 0,
        goalMl = goal?.targetMl ?: 0,
        units = units.name,
        containers = containers.sortedBy { it.sortOrder }.take(WearState.MAX_TILE_BUTTONS).map { WearContainer(it.name, it.volumeMl) },
        updatedAtMillis = nowMillis,
    )

/** Sends [WearState] to the watch. */
fun interface WearStatePublisher {
    suspend fun publish(state: WearState)
}

class DataLayerWearStatePublisher @Inject constructor(@ApplicationContext private val context: Context) : WearStatePublisher {
    override suspend fun publish(state: WearState) {
        val request = PutDataMapRequest.create(WearProtocol.STATE).apply {
            dataMap.putByteArray(WearProtocol.KEY_PAYLOAD, WearProtocol.encode(state))
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request).await()
    }
}

/**
 * Publishes today's state whenever it changes, for as long as the app process lives. A device without Google Play
 * services or without a watch just fails the publish quietly; the data item syncs once a watch connects.
 */
@Singleton
class WearStateSync
@Inject
constructor(
    private val dayTicker: DayTicker,
    private val intake: IntakeRepository,
    private val goals: GoalRepository,
    private val containers: ContainerRepository,
    private val settings: SettingsRepository,
    private val publisher: WearStatePublisher,
    private val clock: Clock,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val last = AtomicReference<WearState?>(null)

    fun start() {
        scope.launch {
            states().distinctUntilChanged { a, b -> a.copy(updatedAtMillis = 0) == b.copy(updatedAtMillis = 0) }.collectLatest(::send)
        }
    }

    /** Re-sends the latest state, for a watch that asked for it. */
    suspend fun republish() {
        last.get()?.let { send(it.copy(updatedAtMillis = clock.millis())) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun states(): Flow<WearState> = dayTicker.today().flatMapLatest { day ->
        combine(intake.observeDailyTotal(day), goals.observeGoal(day), containers.observeAll(), settings.settings) { total, goal, list, s ->
            buildWearState(day, total, goal, s.units, list, clock.millis())
        }
    }

    private suspend fun send(state: WearState) {
        last.set(state)
        try {
            publisher.publish(state)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception, // ApiException without Play services, RemoteException
        ) {
            Log.d(TAG, "Wear state not published: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "WearStateSync"
    }
}
