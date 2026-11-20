package casa.peterson.dhmo.feature.settings.updates

import casa.peterson.dhmo.core.data.repository.InstallResult
import casa.peterson.dhmo.core.data.repository.UpdateController
import casa.peterson.dhmo.core.data.repository.UpdateStatus
import casa.peterson.dhmo.core.testing.FakeDeviceStateRepository
import casa.peterson.dhmo.core.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdatesViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val deviceState = FakeDeviceStateRepository()
    private val controller = FakeUpdateController(deviceState)
    private val viewModel by lazy { UpdatesViewModel(deviceState, controller) }

    @Test
    fun `updates are off until the user turns them on`() = runTest {
        viewModel.state.launchIn(backgroundScope)
        runCurrent()
        assertThat(viewModel.state.value.enabled).isFalse()
        assertThat(viewModel.state.value.status).isEqualTo(UpdateStatus.Idle)
    }

    @Test
    fun `opting in is stored`() = runTest {
        viewModel.state.launchIn(backgroundScope)
        viewModel.setEnabled(true)
        runCurrent()
        assertThat(viewModel.state.value.enabled).isTrue()
    }

    @Test
    fun `install asks for the install permission when Android needs it`() = runTest {
        viewModel.state.launchIn(backgroundScope)
        controller.installResult = InstallResult.NEEDS_PERMISSION
        viewModel.install()
        runCurrent()
        assertThat(viewModel.state.value.needsInstallPermission).isTrue()
        controller.installResult = InstallResult.STARTED
        viewModel.install()
        runCurrent()
        assertThat(viewModel.state.value.needsInstallPermission).isFalse()
    }

    @Test
    fun `installer failure is shown and clears after a successful retry`() = runTest {
        viewModel.state.launchIn(backgroundScope)
        controller.installResult = InstallResult.NOTHING_TO_INSTALL
        viewModel.install()
        runCurrent()
        assertThat(viewModel.state.value.installFailed).isTrue()
        controller.installResult = InstallResult.STARTED
        viewModel.install()
        runCurrent()
        assertThat(viewModel.state.value.installFailed).isFalse()
    }

    @Test
    fun `status follows the controller`() = runTest {
        viewModel.state.launchIn(backgroundScope)
        controller.setStatus(UpdateStatus.UpToDate)
        runCurrent()
        assertThat(viewModel.state.value.status).isEqualTo(UpdateStatus.UpToDate)
    }
}

private class FakeUpdateController(private val device: FakeDeviceStateRepository) : UpdateController {
    override val status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    var installResult = InstallResult.NOTHING_TO_INSTALL

    override suspend fun setEnabled(enabled: Boolean) {
        device.update { it.copy(updates = it.updates.copy(enabled = enabled)) }
    }

    fun setStatus(value: UpdateStatus) {
        status.value = value
    }

    override suspend fun checkIfStale() = Unit
    override suspend fun checkNow() = Unit
    override suspend fun download() = Unit
    override suspend fun dismiss() = Unit
    override fun install(): InstallResult = installResult
}
