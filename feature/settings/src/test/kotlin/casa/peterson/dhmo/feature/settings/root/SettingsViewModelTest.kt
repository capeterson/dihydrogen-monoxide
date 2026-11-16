package casa.peterson.dhmo.feature.settings.root

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.settings.ReminderSettings
import casa.peterson.dhmo.core.model.sync.DriveBackupState
import casa.peterson.dhmo.core.model.sync.HcAvailability
import casa.peterson.dhmo.core.testing.FakeDriveBackupController
import casa.peterson.dhmo.core.testing.FakeHealthConnectController
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val settings = FakeSettingsRepository(AppSettings(reminders = ReminderSettings(enabled = true)))
    private val healthConnect = FakeHealthConnectController(availability = HcAvailability.PROVIDER_UPDATE_REQUIRED)
    private val drive = FakeDriveBackupController(DriveBackupState.NotConfigured)
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun exposesSettingsAndIntegrationAvailability() = runTest {
        val model = SettingsViewModel(settings, healthConnect, drive, context)
        model.uiState.launchIn(backgroundScope)
        advanceUntilIdle()
        val state = model.uiState.value
        assertThat(state.settings?.reminders?.enabled).isTrue()
        assertThat(state.hcAvailability).isEqualTo(HcAvailability.PROVIDER_UPDATE_REQUIRED)
        assertThat(state.driveState).isEqualTo(DriveBackupState.NotConfigured)
        assertThat(state.versionName).isEqualTo(appVersionName(context))
    }

    @Test
    fun followsDriveStateAndRereadsAvailability() = runTest {
        val model = SettingsViewModel(settings, healthConnect, drive, context)
        model.uiState.launchIn(backgroundScope)
        advanceUntilIdle()
        healthConnect.availability = HcAvailability.AVAILABLE
        drive.stateFlow.value = DriveBackupState.NeedsReconnect
        advanceUntilIdle()
        assertThat(model.uiState.value.driveState).isEqualTo(DriveBackupState.NeedsReconnect)
        assertThat(model.uiState.value.hcAvailability).isEqualTo(HcAvailability.AVAILABLE)
    }

    @Test
    fun startsWithoutSettingsUntilTheFirstRead() {
        val model = SettingsViewModel(settings, healthConnect, drive, context)
        assertThat(model.uiState.value.settings).isNull()
    }

    @Test
    fun integrationLabelsCoverEveryState() {
        assertThat(HcAvailability.entries.map { healthConnectLabel(it, connected = false) }).containsNoDuplicates()
        assertThat(healthConnectLabel(HcAvailability.AVAILABLE, connected = true))
            .isNotEqualTo(healthConnectLabel(HcAvailability.AVAILABLE, connected = false))
        val driveStates = listOf(
            DriveBackupState.NotConfigured,
            DriveBackupState.SignedOut,
            DriveBackupState.Ready(null),
            DriveBackupState.Running,
            DriveBackupState.NeedsReconnect,
        )
        assertThat(driveStates.map(::driveLabel)).containsNoDuplicates()
    }
}
