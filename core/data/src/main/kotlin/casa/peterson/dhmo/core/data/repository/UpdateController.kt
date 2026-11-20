package casa.peterson.dhmo.core.data.repository

import kotlinx.coroutines.flow.StateFlow

/** A newer GitHub debug release with a compatible phone APK. */
data class AvailableUpdate(
    val tag: String,
    val name: String,
    val notes: String,
    val publishedAtMs: Long,
    val apkName: String,
    val apkSizeBytes: Long,
)

enum class UpdateFailure { NETWORK, HTTP, PARSE, NO_APK, DOWNLOAD, CHECKSUM, WRONG_SIGNATURE, UNSUPPORTED }

sealed interface UpdateStatus {
    /** Feature off, or nothing checked yet this session. */
    data object Idle : UpdateStatus

    data object Checking : UpdateStatus

    data object UpToDate : UpdateStatus

    data class Available(val update: AvailableUpdate) : UpdateStatus

    /** [percent] is null while the size is unknown. */
    data class Downloading(val update: AvailableUpdate, val percent: Int?) : UpdateStatus

    /** The APK is verified and waiting for the system installer. */
    data class ReadyToInstall(val update: AvailableUpdate) : UpdateStatus

    data class Failed(val reason: UpdateFailure) : UpdateStatus
}

/** Implemented by `:core:update`. Nothing here touches the network while the user has not opted in. */
interface UpdateController {
    val status: StateFlow<UpdateStatus>

    suspend fun setEnabled(enabled: Boolean)

    /** Checks at most every few hours, and only when enabled. Called on app start. */
    suspend fun checkIfStale()

    /** Checks now when enabled; the "Check now" button. */
    suspend fun checkNow()

    /** Downloads and verifies the offered APK, then moves to [UpdateStatus.ReadyToInstall]. */
    suspend fun download()

    /** Hides the current offer until a newer release is published. */
    suspend fun dismiss()

    /** Hands the verified APK to the system installer. Returns false when the user must first allow installs from this app. */
    fun install(): InstallResult
}

enum class InstallResult { STARTED, NEEDS_PERMISSION, NOTHING_TO_INSTALL }
