package casa.peterson.dhmo.sync.drive

import casa.peterson.dhmo.core.backup.BackupCodec
import casa.peterson.dhmo.core.backup.BackupCrypto
import casa.peterson.dhmo.core.backup.BackupException
import casa.peterson.dhmo.core.data.repository.BackupRepository
import casa.peterson.dhmo.core.data.repository.DeviceStateRepository
import casa.peterson.dhmo.core.data.repository.DriveBackupController
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.model.settings.BackupRuntime
import casa.peterson.dhmo.core.model.sync.BackupError
import casa.peterson.dhmo.core.model.sync.DriveBackupState
import casa.peterson.dhmo.core.model.sync.RemoteBackup
import casa.peterson.dhmo.core.model.sync.RestoreMode
import casa.peterson.dhmo.sync.drive.api.DriveApi
import casa.peterson.dhmo.sync.drive.api.DriveFile
import casa.peterson.dhmo.sync.drive.auth.DriveAuth
import casa.peterson.dhmo.sync.drive.crypto.BackupKeyStore
import casa.peterson.dhmo.sync.drive.work.DriveRunState
import casa.peterson.dhmo.sync.drive.work.DriveScheduler
import java.time.Instant
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Google Drive backup: state, schedule, listing, restore and disconnect (TECHNICAL_PLAN §11). */
@Singleton
internal class DriveBackupControllerImpl @Inject constructor(
    private val api: DriveApi,
    private val auth: DriveAuth,
    private val runState: DriveRunState,
    private val scheduler: DriveScheduler,
    private val backups: BackupRepository,
    private val settings: SettingsRepository,
    private val deviceState: DeviceStateRepository,
    private val keyStore: BackupKeyStore,
) : DriveBackupController {
    override val state: Flow<DriveBackupState> = combine(settings.settings, deviceState.state, runState.running) { s, d, running ->
        val runtime = d.backupRuntime
        when {
            !auth.isConfigured -> DriveBackupState.NotConfigured

            runtime.accountEmail == null -> DriveBackupState.SignedOut

            runtime.needsReconnect -> DriveBackupState.NeedsReconnect

            running -> DriveBackupState.Running

            else -> {
                val lastError = runtime.lastError
                if (lastError != null && s.backup.driveEnabled) {
                    DriveBackupState.Error(lastError.toModelError())
                } else {
                    DriveBackupState.Ready(runtime.lastSuccessAtMs?.let(Instant::ofEpochMilli))
                }
            }
        }
    }

    override suspend fun backUpNow() = scheduler.enqueueNow()

    override suspend fun listBackups(): Result<List<RemoteBackup>> = guarded { api.list().map { it.toRemote() } }

    override suspend fun restore(backup: RemoteBackup, mode: RestoreMode, passphrase: CharArray?): Result<Unit> = guarded {
        val bytes = api.download(backup.id)
        val document = BackupCodec.decode(bytes, passphrase)
        backups.restore(document, mode)
    }

    override suspend fun onSettingsChanged() {
        val backup = settings.current().backup
        val signedIn = deviceState.current().backupRuntime.accountEmail != null
        if (backup.driveEnabled && signedIn && auth.isConfigured) {
            scheduler.schedule(backup.frequency, backup.unmeteredOnly)
        } else {
            scheduler.cancel()
        }
    }

    override suspend fun disconnect() {
        scheduler.cancel()
        auth.disconnect()
        deviceState.update { it.copy(backupRuntime = BackupRuntime()) }
        settings.update { it.copy(backup = it.backup.copy(driveEnabled = false)) }
    }

    /**
     * Switches encryption on with [passphrase] (the key is derived once and stored wrapped by the Keystore), or off
     * with null. The passphrase is never stored; a forgotten one makes the encrypted backups unrecoverable.
     */
    suspend fun setEncryption(passphrase: CharArray?) {
        if (passphrase == null) {
            deviceState.update { it.copy(backupRuntime = it.backupRuntime.copy(wrappedKeyB64 = null, kdfSaltB64 = null)) }
            settings.update { it.copy(backup = it.backup.copy(encryptionEnabled = false)) }
            return
        }
        require(passphrase.size >= BackupCrypto.MIN_PASSPHRASE_LENGTH) {
            "Passphrases have at least ${BackupCrypto.MIN_PASSPHRASE_LENGTH} characters"
        }
        val salt = keyStore.newSalt()
        val wrapped = keyStore.wrap(BackupCrypto.deriveKey(passphrase, salt))
        deviceState.update {
            it.copy(backupRuntime = it.backupRuntime.copy(wrappedKeyB64 = wrapped, kdfSaltB64 = Base64.getEncoder().encodeToString(salt)))
        }
        settings.update { it.copy(backup = it.backup.copy(encryptionEnabled = true)) }
    }

    private suspend fun <T> guarded(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: BackupException) {
        Result.failure(e)
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Result.failure(BackupException(casa.peterson.dhmo.core.backup.BackupError.IO, cause = e))
    }

    private fun DriveFile.toRemote() = RemoteBackup(
        id = id,
        name = name,
        createdAt = appProperties["createdAt"]?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: createdAt ?: Instant.EPOCH,
        sizeBytes = sizeBytes,
        encrypted = appProperties["encrypted"] == "true" || name.endsWith(".enc"),
        schemaVersion = appProperties["schemaVersion"]?.toIntOrNull(),
        entryCount = appProperties["entryCount"]?.toIntOrNull(),
    )

    private fun String.toModelError(): BackupError = BackupError.entries.firstOrNull { it.name == this } ?: BackupError.IO
}
