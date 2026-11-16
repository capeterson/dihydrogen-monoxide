package casa.peterson.dhmo.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.data.repository.ActivityRepository
import casa.peterson.dhmo.core.data.repository.GoalRepository
import casa.peterson.dhmo.core.data.repository.ProfileRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.repository.WeatherRepository
import casa.peterson.dhmo.core.data.time.DayTicker
import casa.peterson.dhmo.core.data.usecase.goal.ClearDayOverrideUseCase
import casa.peterson.dhmo.core.data.usecase.goal.SnapshotDailyGoalUseCase
import casa.peterson.dhmo.core.model.ActivityAdjustment
import casa.peterson.dhmo.core.model.ActivitySource
import casa.peterson.dhmo.core.model.ComponentKind
import casa.peterson.dhmo.core.model.DayOverride
import casa.peterson.dhmo.core.model.GoalBreakdown
import casa.peterson.dhmo.core.model.Intensity
import casa.peterson.dhmo.core.model.Profile
import casa.peterson.dhmo.core.model.ResolvedGoal
import casa.peterson.dhmo.core.model.WeatherDay
import casa.peterson.dhmo.core.model.settings.AdjustmentToggles
import casa.peterson.dhmo.core.model.settings.GlobalOverride
import casa.peterson.dhmo.core.model.settings.PinnedInputs
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.core.model.settings.WeatherMode
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Planned exercise accepted for one session, in minutes. */
internal val PLANNED_MINUTES_RANGE = 1..600

sealed interface ProfileGoalUiState {
    data object Loading : ProfileGoalUiState

    data class Ready(
        val day: LocalDate,
        val profile: Profile?,
        /** Null when there is no profile, so nothing was calculated. */
        val breakdown: GoalBreakdown?,
        val effective: ResolvedGoal,
        val globalOverride: GlobalOverride,
        val todayOverride: DayOverride?,
        val toggles: AdjustmentToggles,
        val pinned: PinnedInputs,
        val weather: WeatherDay?,
        val weatherMode: WeatherMode,
        val forecastUnavailable: Boolean,
        /** Today's planned sessions (`MANUAL_PLANNED` rows). */
        val planned: List<ActivityAdjustment>,
        val units: VolumeUnit,
    ) : ProfileGoalUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileGoalViewModel @Inject constructor(
    private val ticker: DayTicker,
    profiles: ProfileRepository,
    private val goals: GoalRepository,
    private val activity: ActivityRepository,
    weather: WeatherRepository,
    settings: SettingsRepository,
    private val snapshot: SnapshotDailyGoalUseCase,
    private val clearDayOverride: ClearDayOverrideUseCase,
) : ViewModel() {
    val uiState: StateFlow<ProfileGoalUiState> = ticker.today()
        // A new day may not have a snapshot yet; computing one is a no-op when nothing changed.
        .onEach { snapshot(it) }
        .flatMapLatest { day ->
            combine(
                goals.observeGoal(day),
                goals.observeDayOverride(day),
                activity.observeDay(day),
                weather.observeDay(day),
                combine(profiles.observeCurrent(), settings.settings, ::Pair),
            ) { goal, override, sessions, forecast, (profile, current) ->
                if (goal == null) {
                    ProfileGoalUiState.Loading
                } else {
                    val breakdown = goal.breakdown.takeIf { goal.calculatedMl != null && it.components.isNotEmpty() }
                    ProfileGoalUiState.Ready(
                        day = day,
                        profile = profile,
                        breakdown = breakdown,
                        effective = ResolvedGoal(goal.targetMl, goal.calculatedMl, goal.mode),
                        globalOverride = current.goal.globalOverride,
                        todayOverride = override,
                        toggles = current.goal.adjustments,
                        pinned = current.goal.pinned,
                        weather = forecast,
                        weatherMode = current.weather.mode,
                        forecastUnavailable = breakdown?.components.orEmpty().any {
                            it.kind == ComponentKind.CLIMATE_MANUAL && it.detail["fallback"] == "true"
                        },
                        planned = sessions.filter { it.source == ActivitySource.MANUAL_PLANNED },
                        units = current.units,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileGoalUiState.Loading)

    /** Adds a planned session for today. Returns false, without writing, when [minutes] is out of range. */
    fun onAddPlanned(minutes: Int, intensity: Intensity): Boolean {
        if (minutes !in PLANNED_MINUTES_RANGE) return false
        viewModelScope.launch {
            val day = ticker.today().first()
            activity.addPlanned(day, minutes, intensity)
            snapshot(day)
        }
        return true
    }

    fun onRemovePlanned(sourceRecordId: String) {
        viewModelScope.launch {
            activity.removePlanned(sourceRecordId)
            snapshot(ticker.today().first())
        }
    }

    fun onClearTodayOverride() {
        viewModelScope.launch { clearDayOverride(ticker.today().first()) }
    }
}
