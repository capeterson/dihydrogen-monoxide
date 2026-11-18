package casa.peterson.dhmo.feature.settings.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import casa.peterson.dhmo.feature.settings.about.AboutScreen
import casa.peterson.dhmo.feature.settings.appearance.AppearanceScreen
import casa.peterson.dhmo.feature.settings.backupfile.BackupSettingsScreen
import casa.peterson.dhmo.feature.settings.export.ExportScreen
import casa.peterson.dhmo.feature.settings.root.SettingsScreen
import casa.peterson.dhmo.feature.settings.stats.StatsSettingsScreen
import casa.peterson.dhmo.feature.settings.units.UnitsSettingsScreen
import casa.peterson.dhmo.feature.settings.weather.CitySearchScreen
import casa.peterson.dhmo.feature.settings.weather.WeatherSettingsScreen

/** Deep link: `dhmo://settings/backup` (the "Reconnect Google Drive" notification). */
fun NavGraphBuilder.settingsScreens(
    navController: NavController,
    onOpenProfile: () -> Unit,
    onOpenReminders: () -> Unit,
    integrations: SettingsIntegrations,
) {
    val back: () -> Unit = { navController.popBackStack() }
    composable<SettingsRoute> {
        SettingsScreen(
            onOpenProfile = onOpenProfile,
            onOpenReminders = onOpenReminders,
            onOpenUnits = { navController.navigate(UnitsSettingsRoute) },
            onOpenAppearance = { navController.navigate(AppearanceRoute) },
            onOpenStats = { navController.navigate(StatsSettingsRoute) },
            onOpenExport = { navController.navigate(ExportRoute) },
            onOpenBackup = { navController.navigate(BackupSettingsRoute) },
            onOpenHealthConnect = { navController.navigate(HealthConnectSettingsRoute) },
            onOpenWeather = { navController.navigate(WeatherSettingsRoute) },
            onOpenAbout = { navController.navigate(AboutRoute) },
        )
    }
    composable<UnitsSettingsRoute> { UnitsSettingsScreen(onBack = back) }
    composable<AppearanceRoute> { AppearanceScreen(onBack = back) }
    composable<StatsSettingsRoute> { StatsSettingsScreen(onBack = back) }
    composable<AboutRoute> { AboutScreen(onBack = back) }
    composable<ExportRoute> { ExportScreen(onBack = back) }
    composable<BackupSettingsRoute>(deepLinks = listOf(navDeepLink<BackupSettingsRoute>(basePath = "dhmo://settings/backup"))) {
        BackupSettingsScreen(
            onBack = back,
            integrations = integrations,
            onOpenRestore = { navController.navigate(RestoreRoute) },
        )
    }
    composable<RestoreRoute> { integrations.driveRestoreContent(back) }
    composable<HealthConnectSettingsRoute> { integrations.healthConnectContent(back) }
    composable<WeatherSettingsRoute> {
        WeatherSettingsScreen(
            onBack = back,
            onOpenCitySearch = { navController.navigate(CitySearchRoute) },
        )
    }
    composable<CitySearchRoute> { CitySearchScreen(onBack = back) }
}
