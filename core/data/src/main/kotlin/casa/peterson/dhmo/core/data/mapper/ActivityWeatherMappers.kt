package casa.peterson.dhmo.core.data.mapper

import casa.peterson.dhmo.core.database.entity.ActivityAdjustmentEntity
import casa.peterson.dhmo.core.database.entity.WeatherDayEntity
import casa.peterson.dhmo.core.model.ActivityAdjustment
import casa.peterson.dhmo.core.model.WeatherDay

fun ActivityAdjustmentEntity.toModel(): ActivityAdjustment = ActivityAdjustment(
    source = source,
    sourceRecordId = sourceRecordId,
    date = date,
    minutes = minutes,
    intensity = intensity,
    extraMl = extraMl,
    exerciseType = exerciseType,
    updatedAt = updatedAt,
)

fun ActivityAdjustment.toEntity(): ActivityAdjustmentEntity = ActivityAdjustmentEntity(
    source = source,
    sourceRecordId = sourceRecordId,
    date = date,
    minutes = minutes,
    intensity = intensity,
    extraMl = extraMl,
    exerciseType = exerciseType,
    updatedAt = updatedAt,
)

fun WeatherDayEntity.toModel(): WeatherDay = WeatherDay(
    date = date,
    latE1 = latE1,
    lonE1 = lonE1,
    maxTempC = maxTempC,
    maxApparentTempC = maxApparentTempC,
    meanRelHumidity = meanRelHumidity,
    extraMl = extraMl,
    fetchedAt = fetchedAt,
)

fun WeatherDay.toEntity(): WeatherDayEntity = WeatherDayEntity(
    date = date,
    latE1 = latE1,
    lonE1 = lonE1,
    maxTempC = maxTempC,
    maxApparentTempC = maxApparentTempC,
    meanRelHumidity = meanRelHumidity,
    extraMl = extraMl,
    fetchedAt = fetchedAt,
)
