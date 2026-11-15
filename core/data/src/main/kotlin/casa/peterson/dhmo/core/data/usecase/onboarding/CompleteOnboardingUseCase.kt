package casa.peterson.dhmo.core.data.usecase.onboarding

import casa.peterson.dhmo.core.data.repository.ContainerRepository
import casa.peterson.dhmo.core.data.repository.DeviceStateRepository
import casa.peterson.dhmo.core.data.repository.ProfileRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.time.DayTicker
import casa.peterson.dhmo.core.data.usecase.goal.SnapshotDailyGoalUseCase
import casa.peterson.dhmo.core.model.Profile
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class CompleteOnboardingUseCase @Inject constructor(
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository,
    private val containers: ContainerRepository,
    private val deviceState: DeviceStateRepository,
    private val ticker: DayTicker,
    private val snapshot: SnapshotDailyGoalUseCase,
) {
    suspend operator fun invoke(profile: Profile?, manualGoalMl: Int?) {
        require((profile == null) != (manualGoalMl == null)) { "Provide a profile or a manual goal" }
        require(manualGoalMl == null || manualGoalMl in MIN_GOAL..MAX_GOAL)
        val today = ticker.today().first()
        if (profile != null) profiles.save(profile, today)
        settings.update {
            val override = it.goal.globalOverride.copy(
                enabled = manualGoalMl != null,
                targetMl =
                    manualGoalMl ?: it.goal.globalOverride.targetMl,
            )
            it.copy(goal = it.goal.copy(globalOverride = override))
        }
        containers.seedDefaultsIfEmpty(settings.current().units)
        snapshot(today)
        deviceState.update { it.copy(onboarding = it.onboarding.copy(completed = true, lastStep = null)) }
    }

    private companion object {
        const val MIN_GOAL = 250
        const val MAX_GOAL = 10_000
    }
}
