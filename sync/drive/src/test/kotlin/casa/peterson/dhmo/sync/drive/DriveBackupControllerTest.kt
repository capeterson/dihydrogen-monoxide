package casa.peterson.dhmo.sync.drive

import casa.peterson.dhmo.core.backup.BackupCodec
import casa.peterson.dhmo.core.backup.BackupCounts
import casa.peterson.dhmo.core.backup.BackupCrypto
import casa.peterson.dhmo.core.backup.BackupDocument
import casa.peterson.dhmo.core.backup.BackupEntities
import casa.peterson.dhmo.core.backup.BackupError
import casa.peterson.dhmo.core.backup.BackupException
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.settings.BackupFrequency
import casa.peterson.dhmo.core.model.settings.BackupRuntime
import casa.peterson.dhmo.core.model.settings.BackupSettings
import casa.peterson.dhmo.core.model.settings.DeviceState
import casa.peterson.dhmo.core.model.sync.DriveBackupState
import casa.peterson.dhmo.core.model.sync.RemoteBackup
import casa.peterson.dhmo.core.model.sync.RestoreMode
import casa.peterson.dhmo.core.testing.FakeBackupRepository
import casa.peterson.dhmo.core.testing.FakeDeviceStateRepository
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.sync.drive.api.DriveApi
import casa.peterson.dhmo.sync.drive.api.DriveFile
import casa.peterson.dhmo.sync.drive.auth.DriveAuth
import casa.peterson.dhmo.sync.drive.work.DriveRunState
import casa.peterson.dhmo.sync.drive.work.DriveScheduler
import casa.peterson.dhmo.sync.drive.crypto.BackupKeyStore
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import javax.crypto.KeyGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DriveBackupControllerTest {
    private class FakeAuth(override var isConfigured: Boolean = true) : DriveAuth {
        var disconnects = 0

        override suspend fun disconnect() {
            disconnects++
        }
    }

    private class RecordingScheduler : DriveScheduler {
        val scheduled = mutableListOf<Pair<BackupFrequency, Boolean>>()
        var cancels = 0

        override fun schedule(frequency: BackupFrequency, unmeteredOnly: Boolean) {
            scheduled += frequency to unmeteredOnly
        }

        override fun cancel() {
            cancels++
        }

        override fun enqueueNow() = Unit
    }

    private val api = mockk<DriveApi>()
    private val auth = FakeAuth()
    private val scheduler = RecordingScheduler()
    private val backups = FakeBackupRepository()
    private val settings = FakeSettingsRepository(AppSettings(backup = BackupSettings(driveEnabled = true, frequency = BackupFrequency.WEEKLY, unmeteredOnly = false)))
    private val deviceState = FakeDeviceStateRepository(DeviceState(backupRuntime = BackupRuntime(accountEmail = "a@b.c")))
    private val software = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val controller = DriveBackupControllerImpl(api, auth, DriveRunState(), scheduler, backups, settings, deviceState, BackupKeyStore { software })

    private val document = BackupDocument(
        appVersion = "1.0.0", appVersionCode = 1, dbVersion = 1, policyVersion = 1, createdAt = Instant.EPOCH, deviceName = "Pixel",
        counts = BackupCounts(0, 0, 0, 0, 0, 0, 0, 0, 0), entities = BackupEntities(), settings = AppSettings(),
    )
    private val remote = RemoteBackup("id", "n", Instant.EPOCH, 10, encrypted = false, schemaVersion = 1, entryCount = 3)

    @Test
    fun `state follows sign-in, reconnect and errors`() = runTest {
        assertThat(controller.state.first()).isEqualTo(DriveBackupState.Ready(null))
        deviceState.update { it.copy(backupRuntime = it.backupRuntime.copy(accountEmail = null)) }
        assertThat(controller.state.first()).isEqualTo(DriveBackupState.SignedOut)
        deviceState.update { it.copy(backupRuntime = BackupRuntime(accountEmail = "a@b.c", needsReconnect = true)) }
        assertThat(controller.state.first()).isEqualTo(DriveBackupState.NeedsReconnect)
        deviceState.update { it.copy(backupRuntime = BackupRuntime(accountEmail = "a@b.c", lastError = "QUOTA")) }
        assertThat(controller.state.first()).isEqualTo(DriveBackupState.Error(casa.peterson.dhmo.core.model.sync.BackupError.QUOTA))
    }

    @Test
    fun `a build without a client id is not configured`() = runTest {
        auth.isConfigured = false
        assertThat(controller.state.first()).isEqualTo(DriveBackupState.NotConfigured)
    }

    @Test
    fun `schedule matches the settings and cancels when switched off or signed out`() = runTest {
        controller.onSettingsChanged()
        assertThat(scheduler.scheduled).containsExactly(BackupFrequency.WEEKLY to false)
        settings.update { it.copy(backup = it.backup.copy(driveEnabled = false)) }
        controller.onSettingsChanged()
        assertThat(scheduler.cancels).isEqualTo(1)
    }

    @Test
    fun `listing maps the app properties`() = runTest {
        coEvery { api.list() } returns listOf(
            DriveFile("a", "hydration-backup-v1-x.json.gz.enc", 5, null, mapOf("createdAt" to "2026-11-15T03:00:00Z", "encrypted" to "true", "entryCount" to "42", "schemaVersion" to "2")),
        )
        val row = controller.listBackups().getOrThrow().single()
        assertThat(row.createdAt).isEqualTo(Instant.parse("2026-11-15T03:00:00Z"))
        assertThat(row.encrypted).isTrue()
        assertThat(row.entryCount).isEqualTo(42)
        assertThat(row.schemaVersion).isEqualTo(2)
    }

    @Test
    fun `restore downloads, decodes and applies with the chosen mode`() = runTest {
        coEvery { api.download("id") } returns BackupCodec.encodeGzip(document)
        assertThat(controller.restore(remote, RestoreMode.MERGE, null).isSuccess).isTrue()
        assertThat(backups.restores.single().second).isEqualTo(RestoreMode.MERGE)
    }

    @Test
    fun `an encrypted backup needs its passphrase and reports a wrong one`() = runTest {
        val salt = ByteArray(16) { it.toByte() }
        val key = BackupCrypto.deriveKey("right passphrase".toCharArray(), salt)
        coEvery { api.download("id") } returns BackupCrypto.encrypt(BackupCodec.encodeGzip(document), key, salt)
        val noPass = controller.restore(remote.copy(encrypted = true), RestoreMode.REPLACE, null)
        assertThat((noPass.exceptionOrNull() as BackupException).error).isEqualTo(BackupError.PASSPHRASE_REQUIRED)
        val wrong = controller.restore(remote.copy(encrypted = true), RestoreMode.REPLACE, "wrong passphrase".toCharArray())
        assertThat((wrong.exceptionOrNull() as BackupException).error).isEqualTo(BackupError.WRONG_PASSPHRASE)
        assertThat(controller.restore(remote.copy(encrypted = true), RestoreMode.REPLACE, "right passphrase".toCharArray()).isSuccess).isTrue()
    }

    @Test
    fun `a network failure while listing is reported as an error`() = runTest {
        coEvery { api.list() } throws BackupException(BackupError.NETWORK)
        assertThat((controller.listBackups().exceptionOrNull() as BackupException).error).isEqualTo(BackupError.NETWORK)
    }

    @Test
    fun `disconnect revokes, clears the account and switches Drive off`() = runTest {
        controller.disconnect()
        assertThat(auth.disconnects).isEqualTo(1)
        assertThat(scheduler.cancels).isEqualTo(1)
        assertThat(deviceState.current().backupRuntime.accountEmail).isNull()
        assertThat(settings.current().backup.driveEnabled).isFalse()
    }

    @Test
    fun `encryption stores a wrapped key and salt, never the passphrase, and can be turned off`() = runTest {
        controller.setEncryption("a long passphrase".toCharArray())
        val runtime = deviceState.current().backupRuntime
        assertThat(runtime.wrappedKeyB64).isNotNull()
        assertThat(runtime.kdfSaltB64).isNotNull()
        assertThat(settings.current().backup.encryptionEnabled).isTrue()
        controller.setEncryption(null)
        assertThat(deviceState.current().backupRuntime.wrappedKeyB64).isNull()
        assertThat(settings.current().backup.encryptionEnabled).isFalse()
    }
}
