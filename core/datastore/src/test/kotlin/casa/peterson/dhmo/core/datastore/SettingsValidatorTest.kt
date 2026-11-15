package casa.peterson.dhmo.core.datastore

import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.settings.BackupSettings
import casa.peterson.dhmo.core.model.settings.GlobalOverride
import casa.peterson.dhmo.core.model.settings.GoalSettings
import casa.peterson.dhmo.core.model.settings.ManualCity
import casa.peterson.dhmo.core.model.settings.ReminderSettings
import casa.peterson.dhmo.core.model.settings.WeatherSettings
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsValidatorTest {
    @Test
    fun `defaults are unchanged`() = assertThat(SettingsValidator.sanitize(AppSettings())).isEqualTo(AppSettings())

    @Test
    fun `day start is clamped`() {
        assertThat(SettingsValidator.sanitize(AppSettings(dayStartMinutes = -5)).dayStartMinutes).isEqualTo(0)
        assertThat(SettingsValidator.sanitize(AppSettings(dayStartMinutes = 1440)).dayStartMinutes).isEqualTo(1439)
    }

    @Test
    fun `global override is clamped and unknown weekdays dropped`() {
        val settings =
            AppSettings(
                goal =
                    GoalSettings(
                        globalOverride = GlobalOverride(true, 50, mapOf("MONDAY" to 99_999, "FUNDAY" to 2000, "SUNDAY" to 1800)),
                    ),
            )
        val result = SettingsValidator.sanitize(settings).goal.globalOverride
        assertThat(result.targetMl).isEqualTo(250)
        assertThat(result.perWeekdayMl).containsExactly("MONDAY", 10_000, "SUNDAY", 1800)
    }

    @Test
    fun `reminder fields are clamped`() {
        val reminders =
            SettingsValidator.sanitize(
                AppSettings(
                    reminders =
                        ReminderSettings(
                            wakeMinutes = 2000,
                            sleepMinutes = -1,
                            intervalFloorMinutes = 1,
                            tolerancePercent = 90,
                            snoozeMinutes = 0,
                        ),
                ),
            ).reminders
        assertThat(reminders.wakeMinutes).isEqualTo(1439)
        assertThat(reminders.sleepMinutes).isEqualTo(0)
        assertThat(reminders.intervalFloorMinutes).isEqualTo(15)
        assertThat(reminders.tolerancePercent).isEqualTo(50)
        assertThat(reminders.snoozeMinutes).isEqualTo(5)
    }

    @Test
    fun `retention counts and manual city are validated`() {
        val result =
            SettingsValidator.sanitize(
                AppSettings(
                    backup = BackupSettings(keepDaily = 0, keepWeekly = 9999, keepMonthly = 6),
                    weather = WeatherSettings(manualCity = ManualCity("Nowhere", latE1 = 5000, lonE1 = 0)),
                ),
            )
        assertThat(result.backup.keepDaily).isEqualTo(1)
        assertThat(result.backup.keepWeekly).isEqualTo(366)
        assertThat(result.backup.keepMonthly).isEqualTo(6)
        assertThat(result.weather.manualCity).isNull()
    }
}
