package casa.peterson.dhmo.feature.settings.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.model.settings.ThemeMode
import casa.peterson.dhmo.core.model.settings.ThemeSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
internal class AppearanceViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val theme: StateFlow<ThemeSettings> =
        settings.settings.map { it.theme }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeSettings())

    fun setMode(mode: ThemeMode) = update { copy(mode = mode) }

    fun setDynamicColor(enabled: Boolean) = update { copy(dynamicColor = enabled) }

    private fun update(transform: ThemeSettings.() -> ThemeSettings) {
        viewModelScope.launch { settings.update { it.copy(theme = it.theme.transform()) } }
    }
}
