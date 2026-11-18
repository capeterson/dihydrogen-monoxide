package casa.peterson.dhmo.core.data.backup

import casa.peterson.dhmo.core.backup.ActivityAdjustmentDto
import casa.peterson.dhmo.core.backup.BackupEntities
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
import casa.peterson.dhmo.core.model.ActivityLevel
import casa.peterson.dhmo.core.model.ActivitySource
import casa.peterson.dhmo.core.model.ClampFlag
import casa.peterson.dhmo.core.model.Climate
import casa.peterson.dhmo.core.model.ContainerIcon
import casa.peterson.dhmo.core.model.Gender
import casa.peterson.dhmo.core.model.GoalBreakdown
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.model.Intensity
import casa.peterson.dhmo.core.model.PregnancyStatus
import casa.peterson.dhmo.core.model.ProfileSource
import casa.peterson.dhmo.core.model.Source
import java.util.UUID

/** Room rows decoded from a [BackupEntities]; rows with an unknown enum value or a malformed id are counted in [skipped]. */
internal data class BackupRows(
    val intakeEntries: List<IntakeEntryEntity>,
    val containers: List<ContainerEntity>,
    val profileSnapshots: List<ProfileSnapshotEntity>,
    val dailyGoals: List<DailyGoalEntity>,
    val dayOverrides: List<DayOverrideEntity>,
    val activityAdjustments: List<ActivityAdjustmentEntity>,
    val weatherDays: List<WeatherDayEntity>,
    val healthDaily: List<HealthDailyEntity>,
    val skipped: Int,
)

/** [hcRecordIds] carries the device-local Health Connect ids of rows that already exist here, keyed by entry id. */
internal fun BackupEntities.toRows(hcRecordIds: Map<UUID, String> = emptyMap()): BackupRows {
    var skipped = 0
    fun <D, E> List<D>.convert(map: (D) -> E?): List<E> = mapNotNull { dto -> map(dto).also { if (it == null) skipped++ } }
    val rows = BackupRows(
        intakeEntries = intakeEntries.convert { it.toEntity(hcRecordIds) },
        containers = containers.convert { it.toEntity() },
        profileSnapshots = profileSnapshots.convert { it.toEntity() },
        dailyGoals = dailyGoals.convert { it.toEntity() },
        dayOverrides = dayOverrides.map { it.toEntity() },
        activityAdjustments = activityAdjustments.convert { it.toEntity() },
        weatherDays = weatherDays.map { it.toEntity() },
        healthDaily = healthDaily.map { it.toEntity() },
        skipped = 0,
    )
    return rows.copy(skipped = skipped)
}

private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? = enumValues<T>().firstOrNull { it.name == name }

private fun uuidOrNull(text: String): UUID? = runCatching { UUID.fromString(text) }.getOrNull()

private fun IntakeEntryDto.toEntity(hcRecordIds: Map<UUID, String>): IntakeEntryEntity? {
    val uuid = uuidOrNull(id)
    val sourceValue = enumOrNull<Source>(source)
    if (uuid == null || sourceValue == null) return null
    return IntakeEntryEntity(
        id = uuid,
        instant = instant,
        zoneOffsetSeconds = zoneOffsetSeconds,
        hydrationDay = hydrationDay,
        volumeMl = volumeMl,
        source = sourceValue,
        hcRecordId = hcRecordIds[uuid],
        hcDataOrigin = hcDataOrigin,
        hcAdopted = hcAdopted,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
}

private fun ContainerDto.toEntity(): ContainerEntity? = ContainerEntity(
    id = uuidOrNull(id) ?: return null,
    name = name,
    volumeMl = volumeMl,
    icon = enumOrNull<ContainerIcon>(icon) ?: return null,
    sortOrder = sortOrder,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

private fun ProfileSnapshotDto.toEntity(): ProfileSnapshotEntity? = ProfileSnapshotEntity(
    id = uuidOrNull(id) ?: return null,
    effectiveFrom = effectiveFrom,
    birthDate = birthDate,
    gender = enumOrNull<Gender>(gender) ?: return null,
    weightKg = weightKg,
    heightCm = heightCm,
    activityLevel = enumOrNull<ActivityLevel>(activityLevel) ?: return null,
    climate = enumOrNull<Climate>(climate) ?: return null,
    pregnancyStatus = enumOrNull<PregnancyStatus>(pregnancyStatus) ?: return null,
    lactating = lactating,
    highAltitude = highAltitude,
    source = enumOrNull<ProfileSource>(source) ?: return null,
    createdAt = createdAt,
)

private fun DailyGoalDto.toEntity(): DailyGoalEntity? = DailyGoalEntity(
    date = date,
    targetMl = targetMl,
    calculatedMl = calculatedMl,
    mode = enumOrNull<GoalMode>(mode) ?: return null,
    breakdownJson = MapperJson.encodeToString(
        GoalBreakdown.serializer(),
        breakdown ?: GoalBreakdown(policyVersion, 0, emptyList(), 0, 0, 0, ClampFlag.NONE),
    ),
    dayStartMinutes = dayStartMinutes,
    policyVersion = policyVersion,
    updatedAt = updatedAt,
)

private fun DayOverrideDto.toEntity() =
    DayOverrideEntity(date = date, targetMl = targetMl, reason = reason, updatedAt = updatedAt, deletedAt = deletedAt)

private fun ActivityAdjustmentDto.toEntity(): ActivityAdjustmentEntity? = ActivityAdjustmentEntity(
    source = enumOrNull<ActivitySource>(source) ?: return null,
    sourceRecordId = sourceRecordId,
    date = date,
    minutes = minutes,
    intensity = enumOrNull<Intensity>(intensity) ?: return null,
    extraMl = extraMl,
    exerciseType = exerciseType,
    updatedAt = updatedAt,
)

private fun WeatherDayDto.toEntity() = WeatherDayEntity(
    date = date,
    latE1 = latE1,
    lonE1 = lonE1,
    maxTempC = maxTempC,
    maxApparentTempC = maxApparentTempC,
    meanRelHumidity = meanRelHumidity,
    extraMl = extraMl,
    fetchedAt = fetchedAt,
)

private fun HealthDailyDto.toEntity() = HealthDailyEntity(date = date, steps = steps, activeKcal = activeKcal, updatedAt = updatedAt)
