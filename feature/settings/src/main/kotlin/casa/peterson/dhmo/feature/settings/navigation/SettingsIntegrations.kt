package casa.peterson.dhmo.feature.settings.navigation

import androidx.compose.runtime.Composable
import casa.peterson.dhmo.core.designsystem.component.ComingSoon

/**
 * Screens whose content needs classes from a sync module (activity-result launchers, Google and Health Connect
 * APIs). `:app` supplies them, so `:feature:settings` never depends on `:sync:*` (TECHNICAL_PLAN §13.4).
 */
class SettingsIntegrations(
    /** The Google Drive section at the top of the Backup screen. Empty when this build has no Drive support. */
    val driveBackupSection: @Composable (onOpenRestore: () -> Unit) -> Unit = { },
    val driveRestoreContent: @Composable (onBack: () -> Unit) -> Unit = { ComingSoon() },
    val healthConnectContent: @Composable (onBack: () -> Unit) -> Unit = { ComingSoon() },
)
