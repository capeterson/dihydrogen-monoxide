package casa.peterson.dhmo.feature.settings.navigation

import kotlinx.serialization.Serializable

@Serializable data object SettingsRoute

/** Units and day start. */
@Serializable data object UnitsSettingsRoute

/** Theme and dynamic colour. */
@Serializable data object AppearanceRoute

/** Phase 4: Drive; Phase 3 shows "restore from file" only. */
@Serializable data object BackupSettingsRoute

/** Phase 4: list of Drive backups. */
@Serializable data object RestoreRoute

/** Phase 5. */
@Serializable data object HealthConnectSettingsRoute

/** Phase 5. */
@Serializable data object WeatherSettingsRoute

/** Phase 5. */
@Serializable data object CitySearchRoute

/** CSV and JSON (Phase 3); PDF (Phase 6). */
@Serializable data object ExportRoute

/** Include no-log days, weekly freeze. */
@Serializable data object StatsSettingsRoute

/** Version, licences, privacy policy, disclaimer. */
@Serializable data object AboutRoute
