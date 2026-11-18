package casa.peterson.dhmo.core.data.mapper

import casa.peterson.dhmo.core.database.entity.DailyGoalEntity
import casa.peterson.dhmo.core.database.entity.DayOverrideEntity
import casa.peterson.dhmo.core.database.entity.ProfileSnapshotEntity
import casa.peterson.dhmo.core.model.DailyGoal
import casa.peterson.dhmo.core.model.DayOverride
import casa.peterson.dhmo.core.model.GoalBreakdown
import casa.peterson.dhmo.core.model.Profile
import casa.peterson.dhmo.core.model.ProfileSnapshot
import kotlinx.serialization.json.Json

fun ProfileSnapshotEntity.toModel(): ProfileSnapshot = ProfileSnapshot(
    id = id,
    effectiveFrom = effectiveFrom,
    profile = Profile(
        birthDate = birthDate,
        gender = gender,
        weightKg = weightKg,
        heightCm = heightCm,
        activityLevel = activityLevel,
        climate = climate,
        pregnancyStatus = pregnancyStatus,
        lactating = lactating,
        highAltitude = highAltitude,
    ),
    source = source,
    createdAt = createdAt,
)

fun ProfileSnapshot.toEntity(): ProfileSnapshotEntity = ProfileSnapshotEntity(
    id = id,
    effectiveFrom = effectiveFrom,
    birthDate = profile.birthDate,
    gender = profile.gender,
    weightKg = profile.weightKg,
    heightCm = profile.heightCm,
    activityLevel = profile.activityLevel,
    climate = profile.climate,
    pregnancyStatus = profile.pregnancyStatus,
    lactating = profile.lactating,
    highAltitude = profile.highAltitude,
    source = source,
    createdAt = createdAt,
)

fun DailyGoalEntity.toModel(): DailyGoal = DailyGoal(
    date = date,
    targetMl = targetMl,
    calculatedMl = calculatedMl,
    mode = mode,
    breakdown = MapperJson.decodeFromString(GoalBreakdown.serializer(), breakdownJson),
    dayStartMinutes = dayStartMinutes,
    policyVersion = policyVersion,
    updatedAt = updatedAt,
)

fun DailyGoal.toEntity(): DailyGoalEntity = DailyGoalEntity(
    date = date,
    targetMl = targetMl,
    calculatedMl = calculatedMl,
    mode = mode,
    breakdownJson = MapperJson.encodeToString(GoalBreakdown.serializer(), breakdown),
    dayStartMinutes = dayStartMinutes,
    policyVersion = policyVersion,
    updatedAt = updatedAt,
)

fun DayOverrideEntity.toModel(): DayOverride = DayOverride(date = date, targetMl = targetMl, reason = reason, updatedAt = updatedAt)

/** The JSON configuration for the `daily_goal.breakdown_json` column. */
internal val MapperJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
