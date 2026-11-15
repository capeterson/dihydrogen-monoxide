package casa.peterson.dhmo.core.data.usecase.intake

import java.util.UUID

/** Validation limits for an intake entry (§4.3). */
object IntakeLimits {
    const val MIN_VOLUME_ML: Int = 1
    const val MAX_VOLUME_ML: Int = 10_000
    const val MAX_NOTE_LENGTH: Int = 200
}

/** Thrown when an edit or delete targets a Health Connect import the user has not adopted (§10.6). */
class ImportNotEditableException(val id: UUID) : IllegalStateException("Entry $id is a read-only import")

/** Thrown when an entry id does not exist. */
class EntryNotFoundException(val id: UUID) : NoSuchElementException("No intake entry $id")
