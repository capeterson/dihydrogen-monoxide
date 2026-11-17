package casa.peterson.dhmo.feature.settings.backupfile

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.backup.BackupCodec
import casa.peterson.dhmo.core.backup.BackupContainer
import casa.peterson.dhmo.core.backup.BackupCrypto
import casa.peterson.dhmo.core.backup.BackupDocument
import casa.peterson.dhmo.core.backup.BackupError
import casa.peterson.dhmo.core.backup.BackupException
import casa.peterson.dhmo.core.common.DhmoDispatcher
import casa.peterson.dhmo.core.common.Dispatcher
import casa.peterson.dhmo.core.data.repository.BackupRepository
import casa.peterson.dhmo.core.model.sync.RestoreMode
import casa.peterson.dhmo.feature.settings.export.DocumentFiles
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the confirmation dialog shows about a decoded backup before anything is changed. */
data class BackupSummary(val createdAt: Instant, val deviceName: String, val appVersion: String, val entryCount: Int)

sealed interface RestoreStep {
    data object Idle : RestoreStep

    data object Reading : RestoreStep

    /** The file is encrypted; [wrongPassphrase] after a failed attempt. */
    data class NeedsPassphrase(val wrongPassphrase: Boolean = false, val tooShort: Boolean = false) : RestoreStep

    data class Confirm(val summary: BackupSummary) : RestoreStep

    data object Restoring : RestoreStep
}

enum class RestoreMessage { RESTORED, MERGED, UNDONE, NOTHING_TO_UNDO, NOT_A_BACKUP, CORRUPT, NEWER_VERSION, READ_FAILED, RESTORE_FAILED }

data class BackupFileUiState(val step: RestoreStep = RestoreStep.Idle, val canUndo: Boolean = false, val message: RestoreMessage? = null)

/** "Restore from file" and "Undo last restore" (TECHNICAL_PLAN §11.9). */
@HiltViewModel
internal class BackupFileViewModel
@Inject
constructor(
    private val backup: BackupRepository,
    private val files: DocumentFiles,
    @param:Dispatcher(DhmoDispatcher.IO) private val io: CoroutineDispatcher,
    @param:Dispatcher(DhmoDispatcher.Default) private val cpu: CoroutineDispatcher,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BackupFileUiState())
    val uiState: StateFlow<BackupFileUiState> = _uiState.asStateFlow()

    /** The picked file while a passphrase is asked for; never put in UI state. */
    private var encrypted: ByteArray? = null
    private var document: BackupDocument? = null

    init {
        refreshUndo()
    }

    fun onFilePicked(uri: Uri?) {
        if (uri == null || _uiState.value.step != RestoreStep.Idle) return
        _uiState.update { it.copy(step = RestoreStep.Reading) }
        viewModelScope.launch {
            val bytes = try {
                withContext(io) { files.read(uri, BackupCodec.MAX_DECODE_BYTES) }
            } catch (e: IOException) {
                Log.w(TAG, "Could not read backup file", e)
                return@launch finish(RestoreMessage.READ_FAILED)
            } catch (e: SecurityException) {
                Log.w(TAG, "Could not read backup file", e)
                return@launch finish(RestoreMessage.READ_FAILED)
            }
            if (BackupCodec.detect(bytes) == BackupContainer.ENCRYPTED) {
                encrypted = bytes
                _uiState.update { it.copy(step = RestoreStep.NeedsPassphrase()) }
            } else {
                decode(bytes, null)
            }
        }
    }

    fun submitPassphrase(passphrase: CharArray) {
        val bytes = encrypted
        if (bytes == null || _uiState.value.step !is RestoreStep.NeedsPassphrase) return
        if (BackupCrypto.isAcceptablePassphrase(passphrase)) {
            _uiState.update { it.copy(step = RestoreStep.Reading) }
            viewModelScope.launch { decode(bytes, passphrase) }
        } else {
            passphrase.fill(' ')
            _uiState.update { it.copy(step = RestoreStep.NeedsPassphrase(tooShort = true)) }
        }
    }

    fun confirm(mode: RestoreMode) {
        val doc = document ?: return
        if (_uiState.value.step !is RestoreStep.Confirm) return
        _uiState.update { it.copy(step = RestoreStep.Restoring) }
        viewModelScope.launch {
            val ok = runOperation { backup.restore(doc, mode) }
            document = null
            finish(
                if (!ok) {
                    RestoreMessage.RESTORE_FAILED
                } else if (mode ==
                    RestoreMode.MERGE
                ) {
                    RestoreMessage.MERGED
                } else {
                    RestoreMessage.RESTORED
                },
            )
        }
    }

    fun cancel() {
        encrypted = null
        document = null
        _uiState.update { it.copy(step = RestoreStep.Idle) }
    }

    fun undoLastRestore() {
        if (_uiState.value.step != RestoreStep.Idle) return
        _uiState.update { it.copy(step = RestoreStep.Restoring) }
        viewModelScope.launch {
            var undone = false
            val ok = runOperation { undone = backup.undoLastRestore() }
            finish(
                when {
                    !ok -> RestoreMessage.RESTORE_FAILED
                    undone -> RestoreMessage.UNDONE
                    else -> RestoreMessage.NOTHING_TO_UNDO
                },
            )
        }
    }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    private suspend fun decode(bytes: ByteArray, passphrase: CharArray?) {
        val result = try {
            Result.success(withContext(cpu) { BackupCodec.decode(bytes, passphrase) })
        } catch (e: BackupException) {
            Result.failure(e)
        } finally {
            passphrase?.fill(' ')
        }
        val doc = result.getOrNull()
        if (doc != null) {
            encrypted = null
            document = doc
            val summary = BackupSummary(doc.createdAt, doc.deviceName, doc.appVersion, doc.counts.intakeEntries)
            _uiState.update { it.copy(step = RestoreStep.Confirm(summary)) }
        } else {
            rejected((result.exceptionOrNull() as BackupException).error)
        }
    }

    private fun rejected(error: BackupError) {
        Log.w(TAG, "Backup file rejected: $error")
        when (error) {
            BackupError.WRONG_PASSPHRASE -> _uiState.update { it.copy(step = RestoreStep.NeedsPassphrase(wrongPassphrase = true)) }
            BackupError.NOT_A_BACKUP -> finish(RestoreMessage.NOT_A_BACKUP)
            BackupError.NEWER_VERSION -> finish(RestoreMessage.NEWER_VERSION)
            else -> finish(RestoreMessage.CORRUPT)
        }
    }

    private suspend fun runOperation(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception, // any failure is reported, the transaction rolled back
    ) {
        Log.w(TAG, "Restore failed", e)
        false
    }

    private fun finish(message: RestoreMessage) {
        encrypted = null
        _uiState.update { it.copy(step = RestoreStep.Idle, message = message) }
        refreshUndo()
    }

    private fun refreshUndo() {
        viewModelScope.launch {
            val available = runCatching { withContext(io) { backup.latestSafetySnapshot() != null } }.getOrDefault(false)
            _uiState.update { it.copy(canUndo = available) }
        }
    }

    private companion object {
        const val TAG = "BackupFileViewModel"
    }
}
