package casa.peterson.dhmo.feature.settings.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.model.settings.StatsSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
internal class StatsSettingsViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val stats: StateFlow<StatsSettings> =
        settings.settings.map { it.stats }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsSettings())

    fun setIncludeNoLogDays(enabled: Boolean) = update { copy(includeNoLogDays = enabled) }

    fun setWeeklyStreakFreeze(enabled: Boolean) = update { copy(weeklyStreakFreeze = enabled) }

    private fun update(transform: StatsSettings.() -> StatsSettings) {
        viewModelScope.launch { settings.update { it.copy(stats = it.stats.transform()) } }
    }
}
