package casa.peterson.dhmo.core.data.usecase.goal

import casa.peterson.dhmo.core.data.repository.GoalRepository
import casa.peterson.dhmo.core.data.repository.ProfileRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.time.DayTicker
import casa.peterson.dhmo.core.model.Profile
import casa.peterson.dhmo.core.model.settings.GlobalOverride
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class SaveProfileUseCase @Inject constructor(
    private val profiles: ProfileRepository,
    private val ticker: DayTicker,
    private val snapshot: SnapshotDailyGoalUseCase,
) {
    suspend operator fun invoke(profile: Profile) {
        val today = ticker.today().first()
        profiles.save(profile, today)
        snapshot(today)
    }
}

class SetGlobalOverrideUseCase @Inject constructor(
    private val settings: SettingsRepository,
    private val ticker: DayTicker,
    private val snapshot: SnapshotDailyGoalUseCase,
) {
    suspend operator fun invoke(override: GlobalOverride) {
        require(override.targetMl in TARGET_RANGE && override.perWeekdayMl.values.all { it in TARGET_RANGE })
        settings.update { it.copy(goal = it.goal.copy(globalOverride = override)) }
        snapshot(ticker.today().first())
    }
}

class SetDayOverrideUseCase @Inject constructor(
    private val goals: GoalRepository,
    private val ticker: DayTicker,
    private val snapshot: SnapshotDailyGoalUseCase,
) {
    suspend operator fun invoke(day: LocalDate, targetMl: Int, reason: String? = null) {
        goals.setDayOverride(day, targetMl, reason)
        snapshot(day, notifyReminders = day == ticker.today().first())
    }
}

class ClearDayOverrideUseCase @Inject constructor(
    private val goals: GoalRepository,
    private val ticker: DayTicker,
    private val snapshot: SnapshotDailyGoalUseCase,
) {
    suspend operator fun invoke(day: LocalDate) {
        goals.clearDayOverride(day)
        snapshot(day, notifyReminders = day == ticker.today().first())
    }
}

private val TARGET_RANGE = 250..10_000
