package casa.peterson.dhmo.core.datastore

import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.settings.GlobalOverride
import casa.peterson.dhmo.core.model.settings.ManualCity
import casa.peterson.dhmo.core.model.settings.ReminderSettings
import java.time.DayOfWeek

/** Clamps every numeric field to its documented range (TECHNICAL_PLAN §5.2). */
object SettingsValidator {
    const val MAX_MINUTE_OF_DAY = 1439
    val TARGET_ML_RANGE = 250..10_000
    val INTERVAL_FLOOR_RANGE = 15..240
    val TOLERANCE_RANGE = 0..50
    val SNOOZE_RANGE = 5..180
    val KEEP_RANGE = 1..366
    private val LAT_E1_RANGE = -900..900
    private val LON_E1_RANGE = -1800..1800

    fun sanitize(settings: AppSettings): AppSettings = settings.copy(
        dayStartMinutes = settings.dayStartMinutes.coerceIn(0, MAX_MINUTE_OF_DAY),
        goal = settings.goal.copy(globalOverride = sanitize(settings.goal.globalOverride)),
        reminders = sanitize(settings.reminders),
        backup =
            settings.backup.copy(
                keepDaily = settings.backup.keepDaily.coerceIn(KEEP_RANGE),
                keepWeekly = settings.backup.keepWeekly.coerceIn(KEEP_RANGE),
                keepMonthly = settings.backup.keepMonthly.coerceIn(KEEP_RANGE),
            ),
        weather = settings.weather.copy(manualCity = settings.weather.manualCity?.takeIf(::isValid)),
    )

    private fun sanitize(override: GlobalOverride): GlobalOverride {
        val validDays = DayOfWeek.entries.map { it.name }.toSet()
        return override.copy(
            targetMl = override.targetMl.coerceIn(TARGET_ML_RANGE),
            perWeekdayMl =
                override.perWeekdayMl
                    .filterKeys { it in validDays }
                    .mapValues { it.value.coerceIn(TARGET_ML_RANGE) },
        )
    }

    private fun sanitize(reminders: ReminderSettings): ReminderSettings = reminders.copy(
        wakeMinutes = reminders.wakeMinutes.coerceIn(0, MAX_MINUTE_OF_DAY),
        sleepMinutes = reminders.sleepMinutes.coerceIn(0, MAX_MINUTE_OF_DAY),
        intervalFloorMinutes = reminders.intervalFloorMinutes.coerceIn(INTERVAL_FLOOR_RANGE),
        tolerancePercent = reminders.tolerancePercent.coerceIn(TOLERANCE_RANGE),
        snoozeMinutes = reminders.snoozeMinutes.coerceIn(SNOOZE_RANGE),
    )

    private fun isValid(city: ManualCity): Boolean = city.latE1 in LAT_E1_RANGE && city.lonE1 in LON_E1_RANGE
}
