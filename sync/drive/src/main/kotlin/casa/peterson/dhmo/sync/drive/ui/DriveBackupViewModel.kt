package casa.peterson.dhmo.sync.drive.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.data.repository.DeviceStateRepository
import casa.peterson.dhmo.core.data.repository.DriveBackupController
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.model.settings.BackupFrequency
import casa.peterson.dhmo.core.model.settings.BackupSettings
import casa.peterson.dhmo.core.model.sync.DriveBackupState
import casa.peterson.dhmo.sync.drive.DriveBackupControllerImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal data class DriveBackupUiState(
    val drive: DriveBackupState = DriveBackupState.NotConfigured,
    val settings: BackupSettings = BackupSettings(),
    val email: String? = null,
    val connectFailed: Boolean = false,
)

@HiltViewModel
internal class DriveBackupViewModel @Inject constructor(
    private val controller: DriveBackupController,
    private val impl: DriveBackupControllerImpl,
    private val settings: SettingsRepository,
    deviceState: DeviceStateRepository,
) : ViewModel() {
    private val connectFailed = MutableStateFlow(false)

    val state: StateFlow<DriveBackupUiState> = combine(controller.state, settings.settings, deviceState.state, connectFailed) { drive, s, d, failed ->
        DriveBackupUiState(drive, s.backup, d.backupRuntime.accountEmail, failed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DriveBackupUiState())

    /** Result of the sign-in and consent flow. Connecting also switches automatic backups on and takes a first backup. */
    fun onAuthResult(authorised: Boolean) {
        connectFailed.value = !authorised
        if (!authorised) return
        viewModelScope.launch {
            settings.update { it.copy(backup = it.backup.copy(driveEnabled = true)) }
            controller.onSettingsChanged()
            controller.backUpNow()
        }
    }

    fun setAutomatic(enabled: Boolean) = change { copy(driveEnabled = enabled) }

    fun setFrequency(frequency: BackupFrequency) = change { copy(frequency = frequency) }

    fun setUnmeteredOnly(unmetered: Boolean) = change { copy(unmeteredOnly = unmetered) }

    fun enableEncryption(passphrase: CharArray) {
        viewModelScope.launch {
            impl.setEncryption(passphrase)
            passphrase.fill('\u0000')
        }
    }

    fun disableEncryption() {
        viewModelScope.launch { impl.setEncryption(null) }
    }

    fun backUpNow() {
        viewModelScope.launch { controller.backUpNow() }
    }

    fun disconnect() {
        viewModelScope.launch {
            controller.disconnect()
            connectFailed.value = false
        }
    }

    private fun change(transform: BackupSettings.() -> BackupSettings) {
        viewModelScope.launch {
            settings.update { it.copy(backup = it.backup.transform()) }
            controller.onSettingsChanged()
        }
    }
}
