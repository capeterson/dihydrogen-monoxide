package casa.peterson.dhmo.core.model.sync

import java.time.Instant

/** How a backup, restore or decode failed (§11.3). */
enum class BackupError {
    NOT_A_BACKUP,
    CORRUPT,
    NEWER_VERSION,
    PASSPHRASE_REQUIRED,
    WRONG_PASSPHRASE,
    IO,
    AUTH,
    QUOTA,
    NETWORK,
}

enum class RestoreMode { REPLACE, MERGE }

/** One backup file listed on Google Drive (Phase 4). */
data class RemoteBackup(
    val id: String,
    val name: String,
    val createdAt: Instant,
    val sizeBytes: Long,
    val encrypted: Boolean,
    val schemaVersion: Int?,
    val entryCount: Int?,
)

sealed interface DriveBackupState {
    data object NotConfigured : DriveBackupState

    data object SignedOut : DriveBackupState

    data class Ready(val lastSuccessAt: Instant?) : DriveBackupState

    data object Running : DriveBackupState

    data object NeedsReconnect : DriveBackupState

    data class Error(val error: BackupError) : DriveBackupState
}

enum class HcAvailability { AVAILABLE, PROVIDER_UPDATE_REQUIRED, UNAVAILABLE }

/** One switch of the Health Connect settings screen, each with its own permission (§10.1). */
enum class HcSetting {
    WRITE_HYDRATION,
    IMPORT_HYDRATION,
    SYNC_WEIGHT,
    USE_EXERCISE,
    READ_STEPS_AND_CALORIES,
    BACKGROUND_SYNC,
    IMPORT_HISTORY,
}

enum class HcError { RATE_LIMITED, IO, SECURITY, UNKNOWN }

sealed interface HcSyncState {
    data object Unavailable : HcSyncState

    data object ProviderUpdateRequired : HcSyncState

    /** Available, but every setting is off. */
    data object Disabled : HcSyncState

    /** A setting is on but its permission was revoked. */
    data class NeedsPermission(val missing: Set<String>) : HcSyncState

    data class Idle(val lastSyncAt: Instant?, val pendingOps: Int) : HcSyncState

    data object Syncing : HcSyncState

    data class Error(val reason: HcError, val lastSyncAt: Instant?) : HcSyncState
}
