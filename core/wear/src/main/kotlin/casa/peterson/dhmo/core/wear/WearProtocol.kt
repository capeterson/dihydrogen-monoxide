package casa.peterson.dhmo.core.wear

import casa.peterson.dhmo.core.model.settings.VolumeUnit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The Wearable Data Layer protocol between the phone and the watch (issue #1). The phone is the source of truth:
 *
 * - The watch sends a [LogRequest] as a message on [LOG]; when the phone is unreachable it puts the same payload in a
 *   data item under [PENDING_PREFIX] instead, which the phone consumes and deletes once it reconnects.
 * - The phone publishes [WearState] as the data item [STATE] whenever today's total, goal, units or containers change,
 *   so the tile and complication render offline.
 * - [STATE_REQUEST] asks the phone to publish again (used when a tile is first added).
 */
object WearProtocol {
    const val LOG = "/dhmo/log"
    const val PENDING_PREFIX = "/dhmo/pending/"
    const val STATE = "/dhmo/state"
    const val STATE_REQUEST = "/dhmo/state-request"

    /** Declared by the phone app in `res/values/wear.xml`; the watch sends to the nodes that have it. */
    const val PHONE_CAPABILITY = "dhmo_phone"

    /** The `DataMap` key that holds an encoded payload. */
    const val KEY_PAYLOAD = "payload"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(request: LogRequest): ByteArray = json.encodeToString(LogRequest.serializer(), request).toByteArray()

    fun decodeLog(bytes: ByteArray): LogRequest? = decode(bytes) {
        json.decodeFromString(LogRequest.serializer(), it)
    }?.takeIf { it.isValid() }

    fun encode(state: WearState): ByteArray = json.encodeToString(WearState.serializer(), state).toByteArray()

    fun decodeState(bytes: ByteArray): WearState? = decode(bytes) { json.decodeFromString(WearState.serializer(), it) }

    /** Malformed payloads from another node are dropped, never thrown. */
    private fun <T> decode(bytes: ByteArray, block: (String) -> T): T? = try {
        block(bytes.decodeToString())
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

/** One drink logged on the watch. The id and instant come from the watch, so delivering it twice is harmless. */
@Serializable
data class LogRequest(val id: String, val epochMillis: Long, val offsetSeconds: Int, val volumeMl: Int) {
    val uuid: UUID get() = UUID.fromString(id)
    val instant: Instant get() = Instant.ofEpochMilli(epochMillis)
    val offset: ZoneOffset get() = ZoneOffset.ofTotalSeconds(offsetSeconds)

    fun isValid(): Boolean = runCatching {
        uuid
        offset
    }.isSuccess && volumeMl in 1..MAX_VOLUME_ML

    companion object {
        const val MAX_VOLUME_ML = 10_000

        fun create(volumeMl: Int, at: Instant, offset: ZoneOffset, id: UUID = UUID.randomUUID()) =
            LogRequest(id.toString(), at.toEpochMilli(), offset.totalSeconds, volumeMl)
    }
}

@Serializable
data class WearContainer(val name: String, val volumeMl: Int)

/** Today's state as the phone sees it; [units] is a `VolumeUnit` name. */
@Serializable
data class WearState(
    val day: String,
    val totalMl: Int,
    val goalMl: Int,
    val units: String = VolumeUnit.ML.name,
    val containers: List<WearContainer> = emptyList(),
    val updatedAtMillis: Long = 0,
) {
    val date: LocalDate? get() = runCatching { LocalDate.parse(day) }.getOrNull()

    val unit: VolumeUnit get() = VolumeUnit.entries.firstOrNull { it.name == units } ?: VolumeUnit.ML

    /** 0..1 of the goal; 0 when there is no goal. */
    val progress: Float get() = if (goalMl <= 0) 0f else (totalMl.toFloat() / goalMl).coerceIn(0f, 1f)

    /** The state after the watch logged [volumeMl] locally, before the phone confirms it. */
    fun plus(volumeMl: Int): WearState = copy(totalMl = totalMl + volumeMl)

    /** The default (first) container, or a 250 ml glass when the phone sent none. */
    val defaultContainer: WearContainer get() = containers.firstOrNull() ?: WearContainer("Glass", DEFAULT_ML)

    companion object {
        const val DEFAULT_ML = 250
        const val MAX_TILE_BUTTONS = 3
    }
}
