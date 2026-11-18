package casa.peterson.dhmo.feature.settings.navigation

import androidx.compose.runtime.Composable
import casa.peterson.dhmo.core.designsystem.component.ComingSoon

/**
 * Screens whose content needs classes from a sync module (activity-result launchers, Google and Health Connect
 * APIs). `:app` supplies them, so `:feature:settings` never depends on `:sync:*` (TECHNICAL_PLAN §13.4).
 */
class SettingsIntegrations(
    val driveBackupContent: @Composable (onBack: () -> Unit, onOpenRestore: () -> Unit) -> Unit = { _, _ -> ComingSoon() },
    val driveRestoreContent: @Composable (onBack: () -> Unit) -> Unit = { ComingSoon() },
    val healthConnectContent: @Composable (onBack: () -> Unit) -> Unit = { ComingSoon() },
)
