package casa.peterson.dhmo.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.time.DayTicker
import casa.peterson.dhmo.core.data.usecase.goal.SnapshotDailyGoalUseCase
import casa.peterson.dhmo.core.model.settings.AdjustmentToggles
import casa.peterson.dhmo.core.model.settings.GoalSettings
import casa.peterson.dhmo.core.model.settings.PinnedInputs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Adjustment toggles and pinned inputs. Both act before the calculator, so each change re-snapshots today's goal. */
@HiltViewModel
class AdjustmentsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val ticker: DayTicker,
    private val snapshot: SnapshotDailyGoalUseCase,
) : ViewModel() {
    /** Null while loading. */
    val goalSettings: StateFlow<GoalSettings?> = settings.settings.map { it.goal }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setToggles(toggles: AdjustmentToggles) = change { it.copy(adjustments = toggles) }

    fun setPinned(pinned: PinnedInputs) = change { it.copy(pinned = pinned) }

    private fun change(transform: (GoalSettings) -> GoalSettings) {
        viewModelScope.launch {
            settings.update { it.copy(goal = transform(it.goal)) }
            snapshot(ticker.today().first())
        }
    }
}
