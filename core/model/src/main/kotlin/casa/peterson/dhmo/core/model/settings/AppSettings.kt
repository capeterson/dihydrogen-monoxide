@file:Suppress("MagicNumber") // the defaults below are the documented product values

package casa.peterson.dhmo.core.model.settings

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val schemaVersion: Int = 1,
    val units: VolumeUnit = VolumeUnit.ML, // first run: US_FL_OZ when Locale is en-US
    /** How body weight and height are typed and shown. Storage is always kilograms and centimetres. */
    val bodyUnits: BodyUnits = BodyUnits.METRIC,
    val dayStartMinutes: Int = 0, // 0..1439
    val theme: ThemeSettings = ThemeSettings(),
    val goal: GoalSettings = GoalSettings(),
    val reminders: ReminderSettings = ReminderSettings(),
    val backup: BackupSettings = BackupSettings(),
    val healthConnect: HealthConnectSettings = HealthConnectSettings(),
    val weather: WeatherSettings = WeatherSettings(),
    val stats: StatsSettings = StatsSettings(),
)

@Serializable enum class VolumeUnit { ML, L, US_FL_OZ, IMP_FL_OZ }

@Serializable enum class BodyUnits { METRIC, IMPERIAL }

@Serializable
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM, // SYSTEM, LIGHT, DARK
    val dynamicColor: Boolean = false, // opt-in, Android 12+; the lo-fi palette is the default
    val animationsEnabled: Boolean = true, // ambient room motion and small celebrations; OS reduced motion always wins
)

@Serializable enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class GoalSettings(
    val globalOverride: GlobalOverride = GlobalOverride(),
    val adjustments: AdjustmentToggles = AdjustmentToggles(),
    val pinned: PinnedInputs = PinnedInputs(),
)

@Serializable
data class GlobalOverride(
    val enabled: Boolean = false,
    val targetMl: Int = 2000, // 250..10000
    /** Keys are java.time.DayOfWeek names ("MONDAY".."SUNDAY"). A missing key falls back to targetMl. */
    val perWeekdayMl: Map<String, Int> = emptyMap(),
)

@Serializable
data class AdjustmentToggles(
    val activity: Boolean = true,
    val climate: Boolean = true, // covers both the weather table and the manual climate setting
    val pregnancyLactation: Boolean = true,
    val altitude: Boolean = true,
)

@Serializable
data class PinnedInputs(
    val pinWeight: Boolean = false, // true: ignore Health Connect weight snapshots
    val pinActivityLevel: Boolean = false, // true: always use the profile's activity level, never measured minutes
)

@Serializable
data class ReminderSettings(
    val enabled: Boolean = false, // turned on by onboarding once POST_NOTIFICATIONS is granted
    val wakeMinutes: Int = 7 * 60, // 07:00
    val sleepMinutes: Int = 23 * 60, // 23:00; may be <= wakeMinutes (sleep after midnight)
    val intervalFloorMinutes: Int = 60, // 15..240
    val tolerancePercent: Int = 10, // 0..50, percent of the day's goal
    val curve: PaceCurveSettings = PaceCurveSettings(),
    val escalationEnabled: Boolean = false,
    val calendarBusyEnabled: Boolean = false, // needs READ_CALENDAR
    val endOfDaySummaryEnabled: Boolean = false,
    val snoozeMinutes: Int = 30,
)

@Serializable
data class PaceCurveSettings(
    val type: PaceCurveType = PaceCurveType.DEFAULT, // DEFAULT, LINEAR, CUSTOM
    /** Used only when type == CUSTOM. Validated by PaceCurve.validate (§8.2). */
    val customPoints: List<CurvePoint> = emptyList(),
)

@Serializable enum class PaceCurveType { DEFAULT, LINEAR, CUSTOM }

@Serializable data class CurvePoint(val xPermille: Int, val yPermille: Int)

@Serializable
data class BackupSettings(
    val driveEnabled: Boolean = false,
    val frequency: BackupFrequency = BackupFrequency.DAILY, // DAILY, WEEKLY
    val unmeteredOnly: Boolean = true,
    val encryptionEnabled: Boolean = false, // the key itself is in DeviceState
    val keepDaily: Int = 7,
    val keepWeekly: Int = 4,
    val keepMonthly: Int = 6,
)

@Serializable enum class BackupFrequency { DAILY, WEEKLY }

@Serializable
data class HealthConnectSettings(
    val writeHydration: Boolean = false,
    val importHydration: Boolean = false, // "Count water logged in other apps"; off by default
    /** Package name -> included. Apps are added (as true) the first time a record from them is seen. */
    val importSources: Map<String, Boolean> = emptyMap(),
    val syncWeight: Boolean = false,
    val useExercise: Boolean = false,
    val readStepsAndCalories: Boolean = false, // correlations only
    val backgroundSync: Boolean = false,
    val importHistory: Boolean = false, // READ_HEALTH_DATA_HISTORY
)

@Serializable
data class WeatherSettings(
    val mode: WeatherMode = WeatherMode.OFF, // OFF, DEVICE_LOCATION, MANUAL_CITY
    val manualCity: ManualCity? = null,
)

@Serializable enum class WeatherMode { OFF, DEVICE_LOCATION, MANUAL_CITY }

@Serializable
data class ManualCity(
    val name: String, // e.g. "Berlin"
    val admin1: String? = null, // e.g. "State of Berlin"
    val countryCode: String? = null, // ISO 3166-1 alpha-2
    val latE1: Int, // latitude × 10, already rounded to 0.1°
    val lonE1: Int,
)

@Serializable
data class StatsSettings(
    val includeNoLogDays: Boolean = false, // PLAN §7.2: excluded from averages by default
    val weeklyStreakFreeze: Boolean = false,
)
