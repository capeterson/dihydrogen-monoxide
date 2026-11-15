package casa.peterson.dhmo.core.data.usecase.goal

import casa.peterson.dhmo.core.data.repository.ActivityRepository
import casa.peterson.dhmo.core.data.repository.HealthConnectController
import casa.peterson.dhmo.core.data.repository.ProfileRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.repository.WeatherRepository
import casa.peterson.dhmo.core.model.ActivityInput
import casa.peterson.dhmo.core.model.ActivitySource
import casa.peterson.dhmo.core.model.ClimateInput
import casa.peterson.dhmo.core.model.ExerciseSession
import casa.peterson.dhmo.core.model.GoalInput
import casa.peterson.dhmo.core.model.settings.WeatherMode
import casa.peterson.dhmo.core.model.sync.HcAvailability
import casa.peterson.dhmo.core.model.sync.HcSetting
import casa.peterson.dhmo.core.model.sync.HcSyncState
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Resolves the inputs that apply to a particular hydration day. */
class BuildGoalInputUseCase @Inject constructor(
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository,
    private val activity: ActivityRepository,
    private val weather: WeatherRepository,
    private val healthConnect: HealthConnectController,
) {
    suspend operator fun invoke(day: LocalDate): GoalInput? {
        val profile = profiles.profileFor(day)?.profile ?: return null
        val current = settings.current()
        val sessions = activity.forDay(day)
        val state = healthConnect.state.first()
        val exercisePermission = healthConnect.permissionsFor(HcSetting.USE_EXERCISE)
        val hasExercise = current.healthConnect.useExercise &&
            healthConnect.availability() == HcAvailability.AVAILABLE &&
            (state !is HcSyncState.NeedsPermission || state.missing.intersect(exercisePermission).isEmpty())
        val useSessions = !current.goal.pinned.pinActivityLevel &&
            (hasExercise || sessions.any { it.source == ActivitySource.MANUAL_PLANNED })
        val activityInput = if (useSessions) {
            ActivityInput.Sessions(sessions.map { ExerciseSession(it.minutes, it.intensity) })
        } else {
            ActivityInput.Level(profile.activityLevel)
        }
        val wantsForecast = current.weather.mode != WeatherMode.OFF
        val forecast = if (wantsForecast) weather.forDay(day) else null
        val climateInput = forecast?.let { ClimateInput.Forecast(it.maxApparentTempC) }
            ?: ClimateInput.Manual(profile.climate, forecastUnavailable = wantsForecast)
        return GoalInput(day, profile, activityInput, climateInput, current.goal.adjustments)
    }
}
