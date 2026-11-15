package casa.peterson.dhmo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.data.repository.DeviceStateRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.model.settings.ThemeSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take

/** Where the app starts. The splash screen stays up while [Loading]. */
enum class StartState { Loading, Onboarding, Main }

@HiltViewModel
class MainViewModel
@Inject
constructor(deviceState: DeviceStateRepository, settings: SettingsRepository) : ViewModel() {
    /** Read once, so finishing onboarding does not rebuild the nav host. */
    val startState: StateFlow<StartState> =
        deviceState.state
            .take(1)
            .map { if (it.onboarding.completed) StartState.Main else StartState.Onboarding }
            .stateIn(viewModelScope, SharingStarted.Eagerly, StartState.Loading)

    val theme: StateFlow<ThemeSettings> =
        settings.settings
            .map { it.theme }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ThemeSettings())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
