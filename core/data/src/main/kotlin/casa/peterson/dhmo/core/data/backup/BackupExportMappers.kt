package casa.peterson.dhmo.core.data.backup

import casa.peterson.dhmo.core.backup.ActivityAdjustmentDto
import casa.peterson.dhmo.core.backup.ContainerDto
import casa.peterson.dhmo.core.backup.DailyGoalDto
import casa.peterson.dhmo.core.backup.DayOverrideDto
import casa.peterson.dhmo.core.backup.HealthDailyDto
import casa.peterson.dhmo.core.backup.IntakeEntryDto
import casa.peterson.dhmo.core.backup.ProfileSnapshotDto
import casa.peterson.dhmo.core.backup.WeatherDayDto
import casa.peterson.dhmo.core.data.mapper.MapperJson
import casa.peterson.dhmo.core.database.entity.ActivityAdjustmentEntity
import casa.peterson.dhmo.core.database.entity.ContainerEntity
import casa.peterson.dhmo.core.database.entity.DailyGoalEntity
import casa.peterson.dhmo.core.database.entity.DayOverrideEntity
import casa.peterson.dhmo.core.database.entity.HealthDailyEntity
import casa.peterson.dhmo.core.database.entity.IntakeEntryEntity
import casa.peterson.dhmo.core.database.entity.ProfileSnapshotEntity
import casa.peterson.dhmo.core.database.entity.WeatherDayEntity
import casa.peterson.dhmo.core.model.GoalBreakdown

/** Mapping between Room rows and the backup DTOs of TECHNICAL_PLAN §11.2. `hc_record_id` is device-local and never exported. */
internal fun IntakeEntryEntity.toDto() = IntakeEntryDto(
    id = id.toString(),
    instant = instant,
    zoneOffsetSeconds = zoneOffsetSeconds,
    hydrationDay = hydrationDay,
    volumeMl = volumeMl,
    source = source.name,
    hcDataOrigin = hcDataOrigin,
    hcAdopted = hcAdopted,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun ContainerEntity.toDto() = ContainerDto(
    id = id.toString(),
    name = name,
    volumeMl = volumeMl,
    icon = icon.name,
    sortOrder = sortOrder,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun ProfileSnapshotEntity.toDto() = ProfileSnapshotDto(
    id = id.toString(),
    effectiveFrom = effectiveFrom,
    birthDate = birthDate,
    gender = gender.name,
    weightKg = weightKg,
    heightCm = heightCm,
    activityLevel = activityLevel.name,
    climate = climate.name,
    pregnancyStatus = pregnancyStatus.name,
    lactating = lactating,
    highAltitude = highAltitude,
    source = source.name,
    createdAt = createdAt,
)

internal fun DailyGoalEntity.toDto() = DailyGoalDto(
    date = date,
    targetMl = targetMl,
    calculatedMl = calculatedMl,
    mode = mode.name,
    breakdown = runCatching { MapperJson.decodeFromString(GoalBreakdown.serializer(), breakdownJson) }.getOrNull(),
    dayStartMinutes = dayStartMinutes,
    policyVersion = policyVersion,
    updatedAt = updatedAt,
)

internal fun DayOverrideEntity.toDto() =
    DayOverrideDto(date = date, targetMl = targetMl, reason = reason, updatedAt = updatedAt, deletedAt = deletedAt)

internal fun ActivityAdjustmentEntity.toDto() = ActivityAdjustmentDto(
    source = source.name,
    sourceRecordId = sourceRecordId,
    date = date,
    minutes = minutes,
    intensity = intensity.name,
    extraMl = extraMl,
    exerciseType = exerciseType,
    updatedAt = updatedAt,
)

internal fun WeatherDayEntity.toDto() = WeatherDayDto(
    date = date,
    latE1 = latE1,
    lonE1 = lonE1,
    maxTempC = maxTempC,
    maxApparentTempC = maxApparentTempC,
    meanRelHumidity = meanRelHumidity,
    extraMl = extraMl,
    fetchedAt = fetchedAt,
)

internal fun HealthDailyEntity.toDto() = HealthDailyDto(date = date, steps = steps, activeKcal = activeKcal, updatedAt = updatedAt)
