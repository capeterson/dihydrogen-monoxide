package casa.peterson.dhmo.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.data.repository.DeviceStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
internal class BatteryHelpViewModel @Inject constructor(private val device: DeviceStateRepository) : ViewModel() {
    fun dismiss() {
        viewModelScope.launch { device.update { it.copy(reminderRuntime = it.reminderRuntime.copy(batteryHintDismissed = true)) } }
    }
}
