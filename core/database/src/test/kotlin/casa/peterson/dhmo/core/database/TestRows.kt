package casa.peterson.dhmo.core.database

import casa.peterson.dhmo.core.database.entity.ContainerEntity
import casa.peterson.dhmo.core.database.entity.IntakeEntryEntity
import casa.peterson.dhmo.core.model.ContainerIcon
import casa.peterson.dhmo.core.model.Source
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal val DAY: LocalDate = LocalDate.of(2026, 6, 10)

internal fun at(day: LocalDate, hour: Int, minute: Int = 0): Instant = day.atTime(hour, minute).toInstant(java.time.ZoneOffset.UTC)

internal fun entry(
    day: LocalDate = DAY,
    hour: Int = 8,
    volumeMl: Int = 250,
    id: UUID = UUID.randomUUID(),
    source: Source = Source.MANUAL,
    deleted: Boolean = false,
    hcRecordId: String? = null,
    hcDataOrigin: String? = null,
    hcAdopted: Boolean = false,
): IntakeEntryEntity {
    val instant = at(day, hour)
    return IntakeEntryEntity(
        id = id,
        instant = instant,
        zoneOffsetSeconds = 0,
        hydrationDay = day,
        volumeMl = volumeMl,
        source = source,
        hcRecordId = hcRecordId,
        hcDataOrigin = hcDataOrigin,
        hcAdopted = hcAdopted,
        createdAt = instant,
        updatedAt = instant,
        deletedAt = if (deleted) instant else null,
    )
}

internal fun container(name: String, sortOrder: Int, volumeMl: Int = 250) =
    ContainerEntity(UUID.randomUUID(), name, volumeMl, ContainerIcon.GLASS, sortOrder, Instant.EPOCH)
