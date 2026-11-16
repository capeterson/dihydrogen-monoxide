package casa.peterson.dhmo.feature.settings.root

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.settings.ReminderSettings
import casa.peterson.dhmo.core.model.settings.StatsSettings
import casa.peterson.dhmo.core.model.settings.ThemeSettings
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.core.model.settings.WeatherMode
import casa.peterson.dhmo.core.model.settings.WeatherSettings
import casa.peterson.dhmo.core.model.sync.DriveBackupState
import casa.peterson.dhmo.core.model.sync.HcAvailability
import casa.peterson.dhmo.core.testing.captureScreenVariants
import casa.peterson.dhmo.feature.settings.about.AboutContent
import casa.peterson.dhmo.feature.settings.appearance.AppearanceContent
import casa.peterson.dhmo.feature.settings.stats.StatsSettingsContent
import casa.peterson.dhmo.feature.settings.units.UnitsContent
import casa.peterson.dhmo.feature.settings.units.UnitsUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

// One class per screen, so each gets a fresh Robolectric renderer (forkEvery = 1).

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenshotTest {
    @Test
    fun root() = captureScreenVariants("SettingsRoot") {
        SettingsContent(
            state = SettingsUiState(
                settings = AppSettings(
                    units = VolumeUnit.ML,
                    dayStartMinutes = 4 * 60,
                    reminders = ReminderSettings(enabled = true),
                    weather = WeatherSettings(mode = WeatherMode.OFF),
                ),
                hcAvailability = HcAvailability.AVAILABLE,
                driveState = DriveBackupState.NotConfigured,
                versionName = "0.1.0",
            ),
            actions = SettingsActions(),
        )
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UnitsSettingsScreenshotTest {
    @Test
    fun units() = captureScreenVariants("SettingsUnits") {
        UnitsContent(UnitsUiState(loading = false, units = VolumeUnit.US_FL_OZ, dayStartMinutes = 0), {}, {}, {}, {}, {})
    }

    @Test
    fun dayStartConfirmation() = captureScreenVariants("SettingsUnitsConfirm") {
        UnitsContent(UnitsUiState(loading = false, dayStartMinutes = 0, pendingDayStart = 4 * 60), {}, {}, {}, {}, {})
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppearanceScreenshotTest {
    @Test
    fun appearance() = captureScreenVariants("SettingsAppearance") {
        AppearanceContent(ThemeSettings(), dynamicColorSupported = true, onBack = {}, onMode = {}, onDynamicColor = {})
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StatsSettingsScreenshotTest {
    @Test
    fun stats() = captureScreenVariants("SettingsStats") {
        StatsSettingsContent(StatsSettings(includeNoLogDays = false, weeklyStreakFreeze = true), {}, {}, {})
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AboutScreenshotTest {
    @Test
    fun about() = captureScreenVariants("SettingsAbout") {
        AboutContent(versionName = "0.1.0", onBack = {}, onOpenPrivacyPolicy = {})
    }

    @Test
    fun licences() = captureScreenVariants("SettingsLicences") {
        AboutContent(versionName = "0.1.0", onBack = {}, onOpenPrivacyPolicy = {}, showLicences = true)
    }
}
