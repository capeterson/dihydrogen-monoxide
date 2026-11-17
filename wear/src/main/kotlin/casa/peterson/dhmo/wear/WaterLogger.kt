package casa.peterson.dhmo.wear

import casa.peterson.dhmo.core.wear.LogRequest
import casa.peterson.dhmo.core.wear.WearState
import java.time.Clock
import java.time.ZoneId

/** Where the watch keeps the last state the phone published, so tiles and complications render offline. */
interface StateStore {
    fun read(): WearState?

    fun write(state: WearState)
}

/** The watch's half of the Data Layer protocol (`WearProtocol`). */
interface PhoneLink {
    /** Sends [request] as a message to a reachable phone; false when none took it. */
    suspend fun send(request: LogRequest): Boolean

    /** Queues [request] as a data item, which the phone consumes when it reconnects. */
    suspend fun queue(request: LogRequest)

    /** Asks the phone to publish its state again. */
    suspend fun requestState()
}

/** Asks the system to redraw the tile and the complications. */
fun interface SurfaceRefresher {
    fun refresh()
}

/**
 * Logs a drink from the watch: the shown total moves at once, then the request goes to the phone as a message, or is
 * queued when the phone is out of reach. The request carries its own UUID and time, so a message and a queued item for
 * the same drink are stored once (`IntakeRepository.addFromWatch`).
 */
class WaterLogger(
    private val store: StateStore,
    private val link: PhoneLink,
    private val refresher: SurfaceRefresher,
    private val clock: Clock,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    suspend fun log(volumeMl: Int): LogRequest {
        val now = clock.instant()
        val request = LogRequest.create(volumeMl, now, zone().rules.getOffset(now))
        store.read()?.let { store.write(it.plus(volumeMl)) }
        refresher.refresh()
        val sent = runCatching { link.send(request) }.getOrDefault(false)
        if (!sent) link.queue(request)
        return request
    }
}
