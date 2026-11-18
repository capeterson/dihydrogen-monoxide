package casa.peterson.dhmo.sync.healthconnect

import casa.peterson.dhmo.core.model.sync.HcAvailability
import casa.peterson.dhmo.core.model.sync.HcSetting
import casa.peterson.dhmo.core.testing.FakeHealthConnectController
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.MainDispatcherRule
import casa.peterson.dhmo.sync.healthconnect.ui.HealthConnectSettingsViewModel
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class HealthConnectSettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val controller = FakeHealthConnectController(availability = HcAvailability.UNAVAILABLE)
    private val viewModel by lazy {
        HealthConnectSettingsViewModel(settings, controller, mockk(relaxed = true), mockk(relaxed = true))
    }

    @Test
    fun `a granted permission turns the switch on, and writing triggers a backfill`() = runTest {
        viewModel.onPermissionResult(HcSetting.WRITE_HYDRATION, setOf("permission.WRITE_HYDRATION"))
        assertThat(settings.current().healthConnect.writeHydration).isTrue()
        assertThat(controller.backfillCount).isEqualTo(1)
        assertThat(controller.settingsChangedCount).isEqualTo(1)
    }

    @Test
    fun `a denied permission leaves the switch off`() = runTest {
        viewModel.onPermissionResult(HcSetting.IMPORT_HYDRATION, emptySet())
        assertThat(settings.current().healthConnect.importHydration).isFalse()
        assertThat(controller.backfillCount).isEqualTo(0)
    }

    @Test
    fun `turning a switch off keeps the others`() = runTest {
        viewModel.onPermissionResult(HcSetting.SYNC_WEIGHT, setOf("permission.SYNC_WEIGHT"))
        viewModel.onPermissionResult(HcSetting.USE_EXERCISE, setOf("permission.USE_EXERCISE"))
        viewModel.turnOff(HcSetting.SYNC_WEIGHT)
        assertThat(settings.current().healthConnect.syncWeight).isFalse()
        assertThat(settings.current().healthConnect.useExercise).isTrue()
    }

    @Test
    fun `sync now asks for an immediate run`() = runTest {
        viewModel.syncNow()
        assertThat(controller.syncRequests).containsExactly(true)
    }
}
