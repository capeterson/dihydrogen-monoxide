package casa.peterson.dhmo.core.backup

import casa.peterson.dhmo.core.model.ClampFlag
import casa.peterson.dhmo.core.model.ComponentKind
import casa.peterson.dhmo.core.model.GoalBreakdown
import casa.peterson.dhmo.core.model.GoalComponent
import casa.peterson.dhmo.core.model.settings.AppSettings
import java.time.Instant
import java.time.LocalDate

fun at(text: String): Instant = Instant.parse(text)

fun day(text: String): LocalDate = LocalDate.parse(text)

fun entryDto(id: String, updatedAt: String, deletedAt: String? = null, volumeMl: Int = 250) = IntakeEntryDto(
    id = id,
    instant = at("2026-11-15T07:05:00Z"),
    zoneOffsetSeconds = 3600,
    hydrationDay = day("2026-11-15"),
    volumeMl = volumeMl,
    source = "MANUAL",
    createdAt = at("2026-11-15T07:05:02Z"),
    updatedAt = at(updatedAt),
    deletedAt = deletedAt?.let(::at),
)

fun emptyDocument(entities: BackupEntities = BackupEntities(), settings: AppSettings = AppSettings()) = BackupDocument(
    appVersion = "1.0.0",
    appVersionCode = 1,
    dbVersion = 1,
    policyVersion = 1,
    createdAt = at("2026-11-15T09:30:00Z"),
    deviceName = "Pixel 8",
    counts = toCounts(entities),
    entities = entities,
    settings = settings,
)

/** A document with every entity type and every optional field set. */
private fun fullEntitiesBase() = BackupEntities(
    intakeEntries = listOf(
        entryDto("5f0c1a52-8f2e-4b6e-9a57-0d7b8f3c2a11", "2026-11-15T07:05:02Z").copy(
            hcDataOrigin = "com.fitbit.FitbitMobile",
            hcAdopted = true,
            note = "after run, \"fast\"",
        ),
        entryDto("6f0c1a52-8f2e-4b6e-9a57-0d7b8f3c2a12", "2026-11-15T08:00:00Z", deletedAt = "2026-11-15T08:00:00Z"),
    ),
    containers = listOf(
        ContainerDto("00000000-0000-4000-8000-000000000001", "Glass", 250, "GLASS", 0, at("2026-11-01T10:00:00Z")),
        ContainerDto(
            "00000000-0000-4000-8000-000000000002",
            "Old",
            500,
            "BOTTLE",
            1,
            at("2026-11-02T10:00:00Z"),
            at("2026-11-03T10:00:00Z"),
        ),
    ),
    profileSnapshots = listOf(
        ProfileSnapshotDto(
            "a0000000-0000-4000-8000-000000000001", day("2026-01-01"), day("1990-05-05"), "FEMALE", 62.5, 170.0,
            "MODERATE", "TEMPERATE", "NONE", lactating = false, highAltitude = true, source = "MANUAL",
            createdAt = at("2026-01-01T00:00:00Z"),
        ),
        ProfileSnapshotDto(
            "a0000000-0000-4000-8000-000000000002", day("2026-06-01"), day("1990-05-05"), "FEMALE", 63.0, null,
            "LIGHT", "WARM", "NONE", lactating = false, highAltitude = false, source = "HEALTH_CONNECT",
            createdAt = at("2026-06-01T00:00:00Z"),
        ),
    ),
    dailyGoals = listOf(
        DailyGoalDto(
            day("2026-11-14"),
            2300,
            2300,
            "CALCULATED",
            GoalBreakdown(
                1,
                36,
                listOf(GoalComponent(ComponentKind.BASE_WEIGHT, 2300, true, mapOf("kg" to "62.5"))),
                2300,
                2300,
                2300,
                ClampFlag.NONE,
            ),
            0,
            1,
            at("2026-11-14T00:00:00Z"),
        ),
        DailyGoalDto(day("2026-11-15"), 2000, null, "GLOBAL_OVERRIDE", null, 240, 1, at("2026-11-15T00:00:00Z")),
    ),
    dayOverrides = listOf(
        DayOverrideDto(day("2026-11-10"), 3000, "hike", at("2026-11-10T06:00:00Z")),
        DayOverrideDto(day("2026-11-11"), 1500, null, at("2026-11-11T06:00:00Z"), at("2026-11-11T07:00:00Z")),
    ),
)

fun fullEntities() = fullEntitiesRest(fullEntitiesBase())

private fun fullEntitiesRest(base: BackupEntities) = base.copy(
    activityAdjustments = listOf(
        ActivityAdjustmentDto("HEALTH_CONNECT", "hc-1", day("2026-11-14"), 30, "MODERATE", 300, 8, at("2026-11-14T10:00:00Z")),
        ActivityAdjustmentDto("MANUAL_PLANNED", "manual-1", day("2026-11-15"), 20, "LIGHT", 100, null, at("2026-11-15T10:00:00Z")),
    ),
    weatherDays = listOf(
        WeatherDayDto(day("2026-11-14"), 525, 134, 21.5, 23.0, 55.0, 200, at("2026-11-14T05:00:00Z")),
        WeatherDayDto(day("2026-11-15"), 525, 134, 18.0, 17.5, null, 0, at("2026-11-15T05:00:00Z")),
    ),
    healthDaily = listOf(
        HealthDailyDto(day("2026-11-14"), 9000L, 450.5, at("2026-11-14T22:00:00Z")),
        HealthDailyDto(day("2026-11-15"), null, null, at("2026-11-15T22:00:00Z")),
    ),
)

fun fileInfo(createdAt: String, schemaVersion: Int = 1, name: String? = null): BackupFileInfo {
    val instant = at(createdAt)
    val stamp = createdAt.replace("-", "").replace(":", "").substringBefore('.').let { if (it.endsWith("Z")) it else it + "Z" }
    return BackupFileInfo(
        id = "id-$createdAt",
        name = name ?: "hydration-backup-v$schemaVersion-$stamp.json.gz",
        createdAt = instant,
        schemaVersion = schemaVersion,
    )
}
