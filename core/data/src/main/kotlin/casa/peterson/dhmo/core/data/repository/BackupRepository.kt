package casa.peterson.dhmo.core.data.repository

import casa.peterson.dhmo.core.backup.BackupDocument
import casa.peterson.dhmo.core.model.sync.RestoreMode
import java.io.File

/** JSON export, restore and safety snapshots (TECHNICAL_PLAN §11.9). */
interface BackupRepository {
    suspend fun exportDocument(): BackupDocument

    /** Writes a safety snapshot first, then applies [doc] in one transaction. */
    suspend fun restore(doc: BackupDocument, mode: RestoreMode)

    /** Restores the newest safety snapshot exactly (no settings sanitising) and consumes it. False when there is none. */
    suspend fun undoLastRestore(): Boolean

    suspend fun writeSafetySnapshot(): File

    suspend fun latestSafetySnapshot(): File?
}
