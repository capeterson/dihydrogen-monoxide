package casa.peterson.dhmo.sync.drive.work

import casa.peterson.dhmo.core.backup.BackupCodec
import casa.peterson.dhmo.core.backup.BackupCrypto
import casa.peterson.dhmo.core.backup.BackupError
import casa.peterson.dhmo.core.backup.BackupException
import casa.peterson.dhmo.core.backup.BackupFileInfo
import casa.peterson.dhmo.core.backup.Retention
import casa.peterson.dhmo.core.data.repository.BackupRepository
import casa.peterson.dhmo.core.data.repository.DeviceStateRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.sync.drive.api.DriveApi
import casa.peterson.dhmo.sync.drive.api.DriveFile
import casa.peterson.dhmo.sync.drive.crypto.BackupKeyStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

internal enum class RunOutcome { SUCCESS, RETRY, FAILURE }

/** One backup to Drive: export, gzip, encrypt when asked, upload, prune (TECHNICAL_PLAN §11.7). */
@Singleton
internal class DriveBackupRunner @Inject constructor(
    private val api: DriveApi,
    private val backups: BackupRepository,
    private val settings: SettingsRepository,
    private val deviceState: DeviceStateRepository,
    private val keyStore: BackupKeyStore,
    private val notifier: ReconnectNotifier,
    private val clock: Clock,
    private val runState: DriveRunState,
) {

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun run(): RunOutcome {
        runState.running.value = true
        return try {
            upload()
            deviceState.update {
                it.copy(
                    backupRuntime = it.backupRuntime.copy(
                        lastSuccessAtMs = clock.millis(),
                        lastError = null,
                        lastErrorAtMs = null,
                        needsReconnect = false,
                    ),
                )
            }
            notifier.cancel()
            RunOutcome.SUCCESS
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackupException) {
            fail(e.error)
        } catch (e: Exception) {
            fail(BackupError.IO)
        } finally {
            runState.running.value = false
        }
    }

    private suspend fun upload() {
        val current = settings.current().backup
        val doc = backups.exportDocument()
        var bytes = BackupCodec.encodeGzip(doc)
        val encrypted = current.encryptionEnabled
        if (encrypted) {
            val runtime = deviceState.current().backupRuntime
            val wrapped = runtime.wrappedKeyB64
            val salt = runtime.kdfSaltB64
            if (wrapped == null || salt == null) throw BackupException(BackupError.PASSPHRASE_REQUIRED, "Encryption is on but has no key")
            bytes = BackupCrypto.encrypt(bytes, keyStore.unwrap(wrapped), java.util.Base64.getDecoder().decode(salt))
        }
        val now = Instant.now(clock)
        val name =
            "hydration-backup-v${doc.schemaVersion}-${STAMP.format(now.atOffset(ZoneOffset.UTC))}.json.gz" + if (encrypted) ".enc" else ""
        api.upload(
            name = name,
            bytes = bytes,
            mimeType = if (encrypted) "application/octet-stream" else "application/gzip",
            appProperties = mapOf(
                "schemaVersion" to doc.schemaVersion.toString(),
                "createdAt" to now.toString(),
                "deviceName" to doc.deviceName.take(DEVICE_NAME_MAX),
                "entryCount" to doc.counts.intakeEntries.toString(),
                "encrypted" to encrypted.toString(),
                "appVersion" to doc.appVersion.take(DEVICE_NAME_MAX),
            ),
        )
        prune(current.keepDaily, current.keepWeekly, current.keepMonthly, doc.schemaVersion)
    }

    /** A failed prune never fails the backup that just succeeded. */
    private suspend fun prune(keepDaily: Int, keepWeekly: Int, keepMonthly: Int, schemaVersion: Int) {
        try {
            val files = api.list()
            val plan = Retention.select(files.map { it.toInfo() }, keepDaily, keepWeekly, keepMonthly, schemaVersion)
            plan.delete.forEach { api.delete(it.id) }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            // Leftover files are pruned by the next run.
        }
    }

    private suspend fun fail(error: BackupError): RunOutcome {
        val now = clock.millis()
        deviceState.update {
            it.copy(
                backupRuntime = it.backupRuntime.copy(
                    lastError = error.name,
                    lastErrorAtMs = now,
                    needsReconnect =
                        error == BackupError.AUTH || it.backupRuntime.needsReconnect,
                ),
            )
        }
        if (error == BackupError.AUTH) notifier.notifyReconnectNeeded()
        return if (error == BackupError.NETWORK) RunOutcome.RETRY else RunOutcome.FAILURE
    }

    private fun DriveFile.toInfo() = BackupFileInfo(
        id = id,
        name = name,
        createdAt = appProperties["createdAt"]?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: createdAt ?: Instant.EPOCH,
        schemaVersion = appProperties["schemaVersion"]?.toIntOrNull() ?: 1,
    )

    private companion object {
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        const val DEVICE_NAME_MAX = 60
    }
}
