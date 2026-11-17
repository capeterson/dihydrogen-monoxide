package casa.peterson.dhmo.feature.settings.backupfile

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.backup.BackupCodec
import casa.peterson.dhmo.core.backup.BackupCounts
import casa.peterson.dhmo.core.backup.BackupCrypto
import casa.peterson.dhmo.core.backup.BackupDocument
import casa.peterson.dhmo.core.backup.BackupEntities
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.sync.RestoreMode
import casa.peterson.dhmo.core.testing.FakeBackupRepository
import casa.peterson.dhmo.core.testing.MainDispatcherRule
import casa.peterson.dhmo.feature.settings.export.FakeDocumentFiles
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupFileViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val doc = BackupDocument(
        appVersion = "1.0.0", appVersionCode = 1, dbVersion = 1, policyVersion = 1, createdAt = Instant.parse("2026-11-15T09:30:00Z"),
        deviceName = "Pixel 8", counts = BackupCounts(42, 1, 3, 1, 1, 0, 0, 0, 0), entities = BackupEntities(), settings = AppSettings(),
    )
    private val backup = FakeBackupRepository()
    private val files = FakeDocumentFiles()
    private val uri = Uri.parse("content://docs/backup")

    private val viewModel by lazy { BackupFileViewModel(backup, files, main.dispatcher, main.dispatcher) }

    @Test
    fun `plain file goes to confirmation and restores with the chosen mode`() = runTest {
        files.readable[uri] = BackupCodec.encodeJson(doc, pretty = true)

        viewModel.onFilePicked(uri)
        assertThat(viewModel.uiState.value.step).isEqualTo(RestoreStep.Confirm(BackupSummary(doc.createdAt, "Pixel 8", "1.0.0", 42)))

        viewModel.confirm(RestoreMode.MERGE)
        assertThat(backup.restores).containsExactly(doc to RestoreMode.MERGE)
        assertThat(viewModel.uiState.value.step).isEqualTo(RestoreStep.Idle)
        assertThat(viewModel.uiState.value.message).isEqualTo(RestoreMessage.MERGED)
    }

    @Test
    fun `encrypted file asks for the passphrase and reports a wrong one`() = runTest {
        val salt = BackupCrypto.newSalt()
        val key = BackupCrypto.deriveKey("correct horse".toCharArray(), salt, iterations = 1_000)
        files.readable[uri] = BackupCrypto.encrypt(BackupCodec.encodeGzip(doc), key, salt, iterations = 1_000)

        viewModel.onFilePicked(uri)
        assertThat(viewModel.uiState.value.step).isEqualTo(RestoreStep.NeedsPassphrase())

        viewModel.submitPassphrase("short".toCharArray())
        assertThat(viewModel.uiState.value.step).isEqualTo(RestoreStep.NeedsPassphrase(tooShort = true))

        viewModel.submitPassphrase("wrong passphrase".toCharArray())
        assertThat(viewModel.uiState.value.step).isEqualTo(RestoreStep.NeedsPassphrase(wrongPassphrase = true))

        viewModel.submitPassphrase("correct horse".toCharArray())
        assertThat(viewModel.uiState.value.step).isInstanceOf(RestoreStep.Confirm::class.java)
        viewModel.confirm(RestoreMode.REPLACE)
        assertThat(backup.restores.single().second).isEqualTo(RestoreMode.REPLACE)
        assertThat(viewModel.uiState.value.message).isEqualTo(RestoreMessage.RESTORED)
    }

    @Test
    fun `rejected files are reported without restoring`() = runTest {
        files.readable[uri] = "hello".toByteArray()
        viewModel.onFilePicked(uri)
        assertThat(viewModel.uiState.value.message).isEqualTo(RestoreMessage.NOT_A_BACKUP)

        files.readable[uri] =
            BackupCodec.encodeJson(doc).decodeToString().replace("\"schemaVersion\":1", "\"schemaVersion\":99").toByteArray()
        viewModel.onFilePicked(uri)
        assertThat(viewModel.uiState.value.message).isEqualTo(RestoreMessage.NEWER_VERSION)

        files.readable[uri] = "{\"format\":\"dhmo-backup\",\"schemaVersion\":1}".toByteArray()
        viewModel.onFilePicked(uri)
        assertThat(viewModel.uiState.value.message).isEqualTo(RestoreMessage.CORRUPT)

        viewModel.onFilePicked(Uri.parse("content://docs/missing"))
        assertThat(viewModel.uiState.value.message).isEqualTo(RestoreMessage.READ_FAILED)
        assertThat(backup.restores).isEmpty()
    }

    @Test
    fun `cancel discards the decoded document`() = runTest {
        files.readable[uri] = BackupCodec.encodeJson(doc)
        viewModel.onFilePicked(uri)
        viewModel.cancel()
        viewModel.confirm(RestoreMode.REPLACE)
        assertThat(backup.restores).isEmpty()
        assertThat(viewModel.uiState.value.step).isEqualTo(RestoreStep.Idle)
    }

    @Test
    fun `undo is offered when a safety snapshot exists`() = runTest {
        backup.snapshotFile = File("pre-restore.json.gz")
        assertThat(viewModel.uiState.value.canUndo).isTrue()

        viewModel.undoLastRestore()
        assertThat(backup.undoCount).isEqualTo(1)
        assertThat(viewModel.uiState.value.message).isEqualTo(RestoreMessage.UNDONE)
        assertThat(viewModel.uiState.value.canUndo).isFalse()
    }
}
