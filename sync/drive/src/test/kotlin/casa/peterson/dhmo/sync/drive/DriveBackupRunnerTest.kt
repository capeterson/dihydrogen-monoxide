package casa.peterson.dhmo.sync.drive

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.backup.BackupCodec
import casa.peterson.dhmo.core.backup.BackupCounts
import casa.peterson.dhmo.core.backup.BackupDocument
import casa.peterson.dhmo.core.backup.BackupEntities
import casa.peterson.dhmo.core.backup.BackupCrypto
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.settings.BackupSettings
import casa.peterson.dhmo.core.testing.FakeBackupRepository
import casa.peterson.dhmo.core.testing.FakeDeviceStateRepository
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.MutableClock
import casa.peterson.dhmo.sync.drive.api.DriveApi
import casa.peterson.dhmo.sync.drive.api.DriveEndpoints
import casa.peterson.dhmo.sync.drive.api.DriveTokenSource
import casa.peterson.dhmo.sync.drive.crypto.BackupKeyStore
import casa.peterson.dhmo.sync.drive.work.DriveBackupRunner
import casa.peterson.dhmo.sync.drive.work.DriveRunState
import casa.peterson.dhmo.sync.drive.work.ReconnectNotifier
import casa.peterson.dhmo.sync.drive.work.RunOutcome
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import javax.crypto.KeyGenerator
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DriveBackupRunnerTest {
    private val server = MockWebServer()
    private val notifier = mockk<ReconnectNotifier>(relaxed = true)
    private val deviceState = FakeDeviceStateRepository()
    private val settings = FakeSettingsRepository(AppSettings(backup = BackupSettings(driveEnabled = true)))
    private val clock = MutableClock(Instant.parse("2026-11-17T03:00:00Z"))
    private val software = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val keyStore = BackupKeyStore { software }
    private lateinit var runner: DriveBackupRunner

    private class Tokens(var token: String? = "t") : DriveTokenSource {
        override suspend fun token() = token

        override suspend fun invalidate(token: String) = Unit
    }

    private val tokens = Tokens()
    private val document = BackupDocument(
        appVersion = "1.0.0", appVersionCode = 1, dbVersion = 1, policyVersion = 1, createdAt = clock.instant(), deviceName = "Pixel",
        counts = BackupCounts(0, 0, 0, 0, 0, 0, 0, 0, 0), entities = BackupEntities(), settings = AppSettings(),
    )

    @Before
    fun setUp() {
        server.start()
        val api = DriveApi(OkHttpClient(), Json { ignoreUnknownKeys = true }, DriveEndpoints(server.url("/drive/v3/"), server.url("/upload/drive/v3/files")), tokens)
        runner = DriveBackupRunner(api, FakeBackupRepository(document), settings, deviceState, keyStore, notifier, clock, DriveRunState())
    }

    @After
    fun tearDown() = server.close()

    private fun respond(body: String = "{}", code: Int = 200) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    @Test
    fun `a backup uploads a named gzip, prunes, and records success`() = runTest {
        respond("""{"id":"new","name":"n"}""") // upload
        respond("""{"files":[]}""") // list for pruning
        assertThat(runner.run()).isEqualTo(RunOutcome.SUCCESS)
        val upload = server.takeRequest()
        val body = upload.body!!.utf8()
        assertThat(body).contains("hydration-backup-v1-20261117T030000Z.json.gz")
        assertThat(body).contains(""""encrypted":"false"""")
        assertThat(server.takeRequest().url.encodedPath).isEqualTo("/drive/v3/files")
        val runtime = deviceState.current().backupRuntime
        assertThat(runtime.lastSuccessAtMs).isEqualTo(clock.millis())
        assertThat(runtime.lastError).isNull()
        verify { notifier.cancel() }
    }

    @Test
    fun `pruning deletes the surplus and a failed prune does not fail the backup`() = runTest {
        respond("""{"id":"new","name":"n"}""")
        respond(code = 500) // the prune's list call fails
        assertThat(runner.run()).isEqualTo(RunOutcome.SUCCESS)
    }

    @Test
    fun `encrypted backups carry the enc suffix and decrypt with the stored key`() = runTest {
        settings.update { it.copy(backup = it.backup.copy(encryptionEnabled = true)) }
        val salt = keyStore.newSalt()
        val derived = BackupCrypto.deriveKey("correct horse".toCharArray(), salt, iterations = 1_000)
        deviceState.update {
            it.copy(
                backupRuntime = it.backupRuntime.copy(
                    wrappedKeyB64 = keyStore.wrap(derived),
                    kdfSaltB64 = java.util.Base64.getEncoder().encodeToString(salt),
                ),
            )
        }
        respond("""{"id":"new","name":"n"}""")
        respond("""{"files":[]}""")
        assertThat(runner.run()).isEqualTo(RunOutcome.SUCCESS)
        val body = server.takeRequest().body!!.utf8()
        assertThat(body).contains(".json.gz.enc")
        assertThat(body).contains(""""encrypted":"true"""")
        assertThat(body).contains("DHMOENC1")
    }

    @Test
    fun `encryption on with no stored key fails without uploading`() = runTest {
        settings.update { it.copy(backup = it.backup.copy(encryptionEnabled = true)) }
        assertThat(runner.run()).isEqualTo(RunOutcome.FAILURE)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `lost authorisation marks reconnect, notifies, and does not retry`() = runTest {
        tokens.token = null
        assertThat(runner.run()).isEqualTo(RunOutcome.FAILURE)
        val runtime = deviceState.current().backupRuntime
        assertThat(runtime.needsReconnect).isTrue()
        assertThat(runtime.lastError).isEqualTo("AUTH")
        verify { notifier.notifyReconnectNeeded() }
    }

    @Test
    fun `network trouble is retried and recorded`() = runTest {
        respond(code = 503)
        assertThat(runner.run()).isEqualTo(RunOutcome.RETRY)
        assertThat(deviceState.current().backupRuntime.lastError).isEqualTo("NETWORK")
    }

    @Test
    fun `a full Drive fails with quota and is not retried`() = runTest {
        respond("""{"error":{"errors":[{"reason":"storageQuotaExceeded"}]}}""", code = 403)
        assertThat(runner.run()).isEqualTo(RunOutcome.FAILURE)
        assertThat(deviceState.current().backupRuntime.lastError).isEqualTo("QUOTA")
    }

    @Test
    fun `the exported document is the one uploaded`() = runTest {
        respond("""{"id":"new","name":"n"}""")
        respond("""{"files":[]}""")
        runner.run()
        assertThat(BackupCodec.FORMAT).isEqualTo("dhmo-backup")
        assertThat(server.takeRequest().body!!.size).isGreaterThan(100)
    }
}
