package casa.peterson.dhmo.core.data.usecase.intake

import casa.peterson.dhmo.core.data.repository.IntakeRepository
import casa.peterson.dhmo.core.model.IntakeEntry
import casa.peterson.dhmo.core.model.Source
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

internal fun validateIntake(volumeMl: Int, note: String?) {
    require(volumeMl in IntakeLimits.MIN_VOLUME_ML..IntakeLimits.MAX_VOLUME_ML) {
        "Volume must be ${IntakeLimits.MIN_VOLUME_ML}..${IntakeLimits.MAX_VOLUME_ML} ml, was $volumeMl"
    }
    require(note == null || note.length <= IntakeLimits.MAX_NOTE_LENGTH) {
        "Note must be at most ${IntakeLimits.MAX_NOTE_LENGTH} characters"
    }
}

private suspend fun IntakeRepository.requireEditable(id: UUID) {
    val entry = get(id)
    if (entry == null || entry.deletedAt != null) throw EntryNotFoundException(id)
    if (!entry.isEditable) throw ImportNotEditableException(id)
}

/** Logs a drink: validates 1 to 10,000 ml, then [IntakeRepository.add]. Throws [IllegalArgumentException] when invalid. */
class LogIntakeUseCase @Inject constructor(private val repository: IntakeRepository) {
    suspend operator fun invoke(volumeMl: Int, source: Source, at: Instant? = null, note: String? = null): IntakeEntry {
        validateIntake(volumeMl, note)
        return repository.add(volumeMl, source, at, note)
    }
}

/** Edits a live entry; unadopted imports throw [ImportNotEditableException]. */
class EditIntakeUseCase @Inject constructor(private val repository: IntakeRepository) {
    suspend operator fun invoke(id: UUID, volumeMl: Int, at: Instant, note: String?): IntakeEntry {
        validateIntake(volumeMl, note)
        repository.requireEditable(id)
        return repository.update(id, volumeMl, at, note)
    }
}

/** Soft-deletes a live entry; unadopted imports throw [ImportNotEditableException]. */
class DeleteIntakeUseCase @Inject constructor(private val repository: IntakeRepository) {
    suspend operator fun invoke(id: UUID) {
        repository.requireEditable(id)
        repository.delete(id)
    }
}

/** Restores a tombstoned entry. */
class UndoDeleteIntakeUseCase @Inject constructor(private val repository: IntakeRepository) {
    suspend operator fun invoke(id: UUID) {
        val entry = repository.get(id) ?: throw EntryNotFoundException(id)
        if (!entry.isEditable) throw ImportNotEditableException(id)
        repository.undoDelete(id)
    }
}
