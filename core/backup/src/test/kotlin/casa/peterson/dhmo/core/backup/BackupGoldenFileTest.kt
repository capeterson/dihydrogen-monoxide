package casa.peterson.dhmo.core.backup

import casa.peterson.dhmo.core.model.ClampFlag
import casa.peterson.dhmo.core.model.ComponentKind
import casa.peterson.dhmo.core.model.GoalBreakdown
import casa.peterson.dhmo.core.model.GoalComponent
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.settings.GlobalOverride
import casa.peterson.dhmo.core.model.settings.GoalSettings
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The fixture `backup-v1.json` is never edited: each later schema version adds its own fixture. */
class BackupGoldenFileTest {
    private val expected = BackupDocument(
        appVersion = "1.0.0",
        appVersionCode = 1,
        dbVersion = 1,
        policyVersion = 1,
        createdAt = at("2026-11-15T09:30:00.123Z"),
        deviceName = "Pixel 8",
        counts = BackupCounts(1, 1, 1, 1, 1, 1, 1, 1, 1),
        entities = BackupEntities(
            intakeEntries = listOf(
                IntakeEntryDto(
                    "5f0c1a52-8f2e-4b6e-9a57-0d7b8f3c2a11", at("2026-11-15T07:05:00Z"), 3600, day("2026-11-15"), 250, "MANUAL",
                    "com.fitbit.FitbitMobile", true, "after run", at("2026-11-15T07:05:02Z"), at("2026-11-15T07:05:02Z"),
                ),
                IntakeEntryDto(
                    "6f0c1a52-8f2e-4b6e-9a57-0d7b8f3c2a12", at("2026-11-15T08:00:00Z"), 0, day("2026-11-15"), 100, "WIDGET",
                    createdAt = at("2026-11-15T08:00:01Z"), updatedAt = at("2026-11-15T08:30:00Z"), deletedAt = at("2026-11-15T08:30:00Z"),
                ),
            ),
            containers = listOf(
                ContainerDto("00000000-0000-4000-8000-000000000001", "Glass", 250, "GLASS", 0, at("2026-11-01T10:00:00Z")),
            ),
            profileSnapshots = listOf(
                ProfileSnapshotDto(
                    "a0000000-0000-4000-8000-000000000001", day("2026-01-01"), day("1990-05-05"), "FEMALE", 62.5, 170.0,
                    "MODERATE", "TEMPERATE", "NONE", lactating = false, highAltitude = false, source = "MANUAL",
                    createdAt = at("2026-01-01T00:00:00Z"),
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
            ),
            dayOverrides = listOf(DayOverrideDto(day("2026-11-10"), 3000, "hike", at("2026-11-10T06:00:00Z"))),
            activityAdjustments = listOf(
                ActivityAdjustmentDto("HEALTH_CONNECT", "hc-1", day("2026-11-14"), 30, "MODERATE", 300, 8, at("2026-11-14T10:00:00Z")),
            ),
            weatherDays = listOf(WeatherDayDto(day("2026-11-14"), 525, 134, 21.5, 23.0, 55.0, 200, at("2026-11-14T05:00:00Z"))),
            healthDaily = listOf(HealthDailyDto(day("2026-11-14"), 9000L, 450.5, at("2026-11-14T22:00:00Z"))),
        ),
        settings = AppSettings(
            units = VolumeUnit.US_FL_OZ,
            dayStartMinutes = 240,
            goal = GoalSettings(globalOverride = GlobalOverride(enabled = true, targetMl = 2500)),
        ),
    )

    @Test
    fun `the committed v1 fixture decodes to the expected document`() {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/backup-v1.json")).use { it.readBytes() }
        val decoded = BackupCodec.decode(bytes)
        assertThat(decoded).isEqualTo(expected)
        assertThat(decoded.counts).isEqualTo(toCounts(decoded.entities))
    }
}
