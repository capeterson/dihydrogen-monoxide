package casa.peterson.dhmo.core.data.repository

import casa.peterson.dhmo.core.model.sync.DriveBackupState
import casa.peterson.dhmo.core.model.sync.RemoteBackup
import casa.peterson.dhmo.core.model.sync.RestoreMode
import kotlinx.coroutines.flow.Flow

/** Implemented by `:sync:drive` (Phase 4). */
interface DriveBackupController {
    val state: Flow<DriveBackupState>

    suspend fun backUpNow()

    suspend fun listBackups(): Result<List<RemoteBackup>>

    suspend fun restore(backup: RemoteBackup, mode: RestoreMode, passphrase: CharArray?): Result<Unit>

    /** (Re)schedule or cancel the periodic work. */
    suspend fun onSettingsChanged()

    suspend fun disconnect()
}
