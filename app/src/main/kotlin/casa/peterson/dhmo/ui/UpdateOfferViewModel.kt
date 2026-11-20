package casa.peterson.dhmo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.data.repository.InstallResult
import casa.peterson.dhmo.core.data.repository.UpdateController
import casa.peterson.dhmo.core.data.repository.UpdateStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Drives the "update available" dialog. Looking for an update does nothing unless the user has opted in. */
@HiltViewModel
class UpdateOfferViewModel @Inject constructor(private val controller: UpdateController) : ViewModel() {
    val status: StateFlow<UpdateStatus> = controller.status
    private val mutableInstallResult = MutableStateFlow<InstallResult?>(null)
    val installResult: StateFlow<InstallResult?> = mutableInstallResult
    private val mutableDownloadStarted = MutableStateFlow(false)
    val downloadStarted: StateFlow<Boolean> = mutableDownloadStarted
    private val mutableHidden = MutableStateFlow(false)
    val hidden: StateFlow<Boolean> = mutableHidden

    init {
        viewModelScope.launch { controller.checkIfStale() }
    }

    fun download() {
        mutableDownloadStarted.value = true
        viewModelScope.launch { controller.download() }
    }

    fun install() {
        mutableInstallResult.value = controller.install()
    }

    fun hide() {
        mutableHidden.value = true
    }

    fun show() {
        mutableHidden.value = false
    }

    fun notNow() {
        mutableDownloadStarted.value = false
        mutableInstallResult.value = null
        viewModelScope.launch { controller.dismiss() }
    }
}
