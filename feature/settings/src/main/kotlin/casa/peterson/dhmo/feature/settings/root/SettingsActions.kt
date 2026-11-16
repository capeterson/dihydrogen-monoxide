package casa.peterson.dhmo.feature.settings.root

/** Callbacks for every row of the settings root, in display order. */
internal class SettingsActions(
    val onOpenProfile: () -> Unit = {},
    val onOpenReminders: () -> Unit = {},
    val onOpenUnits: () -> Unit = {},
    val onOpenAppearance: () -> Unit = {},
    val onOpenStats: () -> Unit = {},
    val onOpenExport: () -> Unit = {},
    val onOpenBackup: () -> Unit = {},
    val onOpenHealthConnect: () -> Unit = {},
    val onOpenWeather: () -> Unit = {},
    val onOpenAbout: () -> Unit = {},
)
