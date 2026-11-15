package casa.peterson.dhmo.sync.drive

import casa.peterson.dhmo.core.data.repository.DriveBackupController
import casa.peterson.dhmo.core.model.sync.BackupError
import casa.peterson.dhmo.core.model.sync.DriveBackupState
import casa.peterson.dhmo.core.model.sync.RemoteBackup
import casa.peterson.dhmo.core.model.sync.RestoreMode
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Replaced by `DriveBackupControllerImpl` in T-DR2. */
internal class NoOpDriveBackupController
@Inject
constructor() : DriveBackupController {
    override val state: Flow<DriveBackupState> = flowOf(DriveBackupState.NotConfigured)

    override suspend fun backUpNow() = Unit

    override suspend fun listBackups(): Result<List<RemoteBackup>> = Result.success(emptyList())

    override suspend fun restore(backup: RemoteBackup, mode: RestoreMode, passphrase: CharArray?): Result<Unit> =
        Result.failure(UnsupportedOperationException(BackupError.AUTH.name))

    override suspend fun onSettingsChanged() = Unit

    override suspend fun disconnect() = Unit
}
