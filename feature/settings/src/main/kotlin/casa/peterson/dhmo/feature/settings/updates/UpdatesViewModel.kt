package casa.peterson.dhmo.feature.settings.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.data.repository.DeviceStateRepository
import casa.peterson.dhmo.core.data.repository.InstallResult
import casa.peterson.dhmo.core.data.repository.UpdateController
import casa.peterson.dhmo.core.data.repository.UpdateStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class UpdatesUiState(
    val enabled: Boolean = false,
    val status: UpdateStatus = UpdateStatus.Idle,
    val lastCheckAtMs: Long? = null,
    /** Set after "Install" when Android first needs "install unknown apps" allowed for this app. */
    val needsInstallPermission: Boolean = false,
    val installFailed: Boolean = false,
)

@HiltViewModel
internal class UpdatesViewModel @Inject constructor(deviceState: DeviceStateRepository, private val controller: UpdateController) :
    ViewModel() {
    private val needsPermission = MutableStateFlow(false)
    private val installFailed = MutableStateFlow(false)

    val state: StateFlow<UpdatesUiState> = combine(
        deviceState.state,
        controller.status,
        needsPermission,
        installFailed,
    ) { device, status, permission, failed ->
        UpdatesUiState(device.updates.enabled, status, device.updates.lastCheckAtMs, permission, failed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UpdatesUiState())

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { controller.setEnabled(enabled) }
    }

    fun checkNow() {
        viewModelScope.launch { controller.checkNow() }
    }

    fun download() {
        viewModelScope.launch { controller.download() }
    }

    fun install() {
        val result = controller.install()
        needsPermission.update { result == InstallResult.NEEDS_PERMISSION }
        installFailed.update { result == InstallResult.NOTHING_TO_INSTALL }
    }

    fun dismiss() {
        viewModelScope.launch { controller.dismiss() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
