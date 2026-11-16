package casa.peterson.dhmo.feature.settings.root

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.data.repository.DriveBackupController
import casa.peterson.dhmo.core.data.repository.HealthConnectController
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.sync.DriveBackupState
import casa.peterson.dhmo.core.model.sync.HcAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** TECHNICAL_PLAN §13.7. [settings] is null until the first DataStore read. */
internal data class SettingsUiState(
    val settings: AppSettings? = null,
    val hcAvailability: HcAvailability = HcAvailability.UNAVAILABLE,
    val driveState: DriveBackupState = DriveBackupState.NotConfigured,
    val versionName: String = "",
)

/** The settings root: every row's summary, with integration availability read from the controller interfaces. */
@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    settings: SettingsRepository,
    healthConnect: HealthConnectController,
    drive: DriveBackupController,
    @ApplicationContext context: Context,
) : ViewModel() {
    private val versionName = appVersionName(context)

    val uiState: StateFlow<SettingsUiState> =
        combine(settings.settings, drive.state) { appSettings, driveState ->
            SettingsUiState(
                settings = appSettings,
                // Availability can change when the provider app is installed or updated, so read it on every emission.
                hcAvailability = healthConnect.availability(),
                driveState = driveState,
                versionName = versionName,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(versionName = versionName))
}

/** The installed app's `versionName`, or an empty string when the package manager has none. */
internal fun appVersionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull().orEmpty()
