package casa.peterson.dhmo.sync.wear

import casa.peterson.dhmo.core.data.repository.IntakeRepository
import casa.peterson.dhmo.core.wear.WearProtocol
import javax.inject.Inject

/** Writes a drink logged on the watch to Room; from there it reaches Health Connect, reminders and backups as usual. */
class WearLogHandler @Inject constructor(private val intake: IntakeRepository) {
    /** True when a new entry was stored; false for a malformed payload or an id that is already known. */
    suspend fun handle(payload: ByteArray): Boolean {
        val request = WearProtocol.decodeLog(payload) ?: return false
        return intake.addFromWatch(request.uuid, request.volumeMl, request.instant, request.offset)
    }
}
