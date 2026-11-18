package casa.peterson.dhmo.sync.drive.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.backup.BackupError
import casa.peterson.dhmo.core.backup.BackupException
import casa.peterson.dhmo.core.data.repository.DeviceStateRepository
import casa.peterson.dhmo.core.data.repository.DriveBackupController
import casa.peterson.dhmo.core.model.sync.RemoteBackup
import casa.peterson.dhmo.core.model.sync.RestoreMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal enum class ListFailure { AUTH, NETWORK }

internal sealed interface RestoreUi {
    data object Loading : RestoreUi

    data class Loaded(val backups: List<RemoteBackup>) : RestoreUi

    data class Failed(val reason: ListFailure) : RestoreUi
}

/** What the user is in the middle of after tapping a backup. */
internal sealed interface RestoreStep {
    data object None : RestoreStep

    data class Passphrase(val backup: RemoteBackup, val wrong: Boolean) : RestoreStep

    data class Confirm(val backup: RemoteBackup, val passphrase: CharArray?) : RestoreStep

    data class Restoring(val backup: RemoteBackup) : RestoreStep

    data class Done(val mode: RestoreMode) : RestoreStep

    data object Failed : RestoreStep
}

/** The highest schema this build can restore (`Retention` and the codec agree on 1 today). */
internal const val SUPPORTED_SCHEMA_VERSION = 1

internal fun RemoteBackup.needsNewerApp(): Boolean = (schemaVersion ?: 1) > SUPPORTED_SCHEMA_VERSION

@HiltViewModel
internal class RestoreListViewModel @Inject constructor(
    private val controller: DriveBackupController,
    private val deviceState: DeviceStateRepository,
) : ViewModel() {
    private val _list = MutableStateFlow<RestoreUi>(RestoreUi.Loading)
    val list: StateFlow<RestoreUi> = _list
    private val _step = MutableStateFlow<RestoreStep>(RestoreStep.None)
    val step: StateFlow<RestoreStep> = _step

    init {
        load()
    }

    fun load() {
        _list.value = RestoreUi.Loading
        viewModelScope.launch {
            controller.listBackups().fold(
                onSuccess = { _list.value = RestoreUi.Loaded(it.sortedByDescending { b -> b.createdAt }) },
                onFailure = {
                    _list.value =
                        RestoreUi.Failed(if ((it as? BackupException)?.error == BackupError.AUTH) ListFailure.AUTH else ListFailure.NETWORK)
                },
            )
        }
    }

    fun select(backup: RemoteBackup) {
        if (backup.needsNewerApp()) return
        _step.value = if (backup.encrypted) RestoreStep.Passphrase(backup, wrong = false) else RestoreStep.Confirm(backup, null)
    }

    fun submitPassphrase(backup: RemoteBackup, passphrase: CharArray) {
        _step.value = RestoreStep.Confirm(backup, passphrase)
    }

    fun restore(mode: RestoreMode) {
        val confirm = _step.value as? RestoreStep.Confirm ?: return
        _step.value = RestoreStep.Restoring(confirm.backup)
        viewModelScope.launch {
            controller.restore(confirm.backup, mode, confirm.passphrase).fold(
                onSuccess = { _step.value = RestoreStep.Done(mode) },
                onFailure = {
                    val error = (it as? BackupException)?.error
                    _step.value = if (error == BackupError.WRONG_PASSPHRASE || error == BackupError.PASSPHRASE_REQUIRED) {
                        RestoreStep.Passphrase(confirm.backup, wrong = true)
                    } else {
                        RestoreStep.Failed
                    }
                },
            )
            confirm.passphrase?.fill('\u0000')
        }
    }

    /** After restoring on a new phone there is nothing left to set up, so onboarding counts as done. */
    fun markOnboardingComplete() {
        viewModelScope.launch { deviceState.update { it.copy(onboarding = it.onboarding.copy(completed = true)) } }
    }

    fun dismiss() {
        _step.value = RestoreStep.None
    }
}
