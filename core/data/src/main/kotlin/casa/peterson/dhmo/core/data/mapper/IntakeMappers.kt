package casa.peterson.dhmo.core.data.mapper

import casa.peterson.dhmo.core.database.entity.ContainerEntity
import casa.peterson.dhmo.core.database.entity.DailyTotalView
import casa.peterson.dhmo.core.database.entity.IntakeEntryEntity
import casa.peterson.dhmo.core.model.Container
import casa.peterson.dhmo.core.model.DailyTotal
import casa.peterson.dhmo.core.model.IntakeEntry
import java.time.ZoneOffset

fun IntakeEntryEntity.toModel(): IntakeEntry = IntakeEntry(
    id = id,
    instant = instant,
    zoneOffset = ZoneOffset.ofTotalSeconds(zoneOffsetSeconds),
    hydrationDay = hydrationDay,
    volumeMl = volumeMl,
    source = source,
    hcRecordId = hcRecordId,
    hcDataOrigin = hcDataOrigin,
    hcAdopted = hcAdopted,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun IntakeEntry.toEntity(): IntakeEntryEntity = IntakeEntryEntity(
    id = id,
    instant = instant,
    zoneOffsetSeconds = zoneOffset.totalSeconds,
    hydrationDay = hydrationDay,
    volumeMl = volumeMl,
    source = source,
    hcRecordId = hcRecordId,
    hcDataOrigin = hcDataOrigin,
    hcAdopted = hcAdopted,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun DailyTotalView.toModel(): DailyTotal =
    DailyTotal(date = date, totalMl = totalMl, entryCount = entryCount, firstAt = firstAt, lastAt = lastAt)

fun ContainerEntity.toModel(): Container =
    Container(id = id, name = name, volumeMl = volumeMl, icon = icon, sortOrder = sortOrder, updatedAt = updatedAt)

fun Container.toEntity(): ContainerEntity = ContainerEntity(
    id = id,
    name = name,
    volumeMl = volumeMl,
    icon = icon,
    sortOrder = sortOrder,
    updatedAt = updatedAt,
    deletedAt = null,
)
