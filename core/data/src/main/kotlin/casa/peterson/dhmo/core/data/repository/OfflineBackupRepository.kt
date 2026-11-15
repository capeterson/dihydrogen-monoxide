package casa.peterson.dhmo.core.data.repository

import casa.peterson.dhmo.core.backup.BackupDocument
import casa.peterson.dhmo.core.model.sync.RestoreMode
import java.io.File
import javax.inject.Inject

/** Stub: the Room-backed implementation arrives with T16. */
class OfflineBackupRepository @Inject constructor() : BackupRepository {
    override suspend fun exportDocument(): BackupDocument = TODO("T16")

    override suspend fun restore(doc: BackupDocument, mode: RestoreMode) = TODO("T16")

    override suspend fun writeSafetySnapshot(): File = TODO("T16")

    override suspend fun latestSafetySnapshot(): File? = TODO("T16")
}
