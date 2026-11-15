package casa.peterson.dhmo.core.data.repository

import casa.peterson.dhmo.core.backup.BackupDocument
import casa.peterson.dhmo.core.model.sync.RestoreMode
import java.io.File

/** Phase 3. */
interface BackupRepository {
    suspend fun exportDocument(): BackupDocument

    suspend fun restore(doc: BackupDocument, mode: RestoreMode)

    suspend fun writeSafetySnapshot(): File

    suspend fun latestSafetySnapshot(): File?
}
