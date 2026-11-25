package casa.peterson.dhmo.core.data

import casa.peterson.dhmo.core.data.repository.OfflineDeviceStateRepository
import casa.peterson.dhmo.core.data.repository.OfflineSettingsRepository
import casa.peterson.dhmo.core.datastore.DeviceStateStore
import casa.peterson.dhmo.core.datastore.SettingsStore
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.settings.DeviceState
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.core.testing.FakeWidgetUpdater
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SettingsRepositoriesTest {
    private class MemorySettingsStore : SettingsStore {
        val flow = MutableStateFlow(AppSettings())
        override val settings = flow

        override suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings = transform(flow.value).also { flow.value = it }
    }

    private class MemoryDeviceStore : DeviceStateStore {
        val flow = MutableStateFlow(DeviceState())
        override val state = flow

        override suspend fun update(transform: (DeviceState) -> DeviceState): DeviceState = transform(flow.value).also { flow.value = it }
    }

    @Test
    fun `settings repository reads and updates the store`() = runTest {
        val widgets = FakeWidgetUpdater()
        val repository = OfflineSettingsRepository(MemorySettingsStore(), widgets)
        assertThat(repository.current().units).isEqualTo(VolumeUnit.ML)
        repository.update { it.copy(units = VolumeUnit.US_FL_OZ) }
        assertThat(repository.current().units).isEqualTo(VolumeUnit.US_FL_OZ)
        assertThat(repository.settings.first().units).isEqualTo(VolumeUnit.US_FL_OZ)
        assertThat(widgets.updateCount).isEqualTo(1)
    }

    @Test
    fun `device state repository reads and updates the store`() = runTest {
        val repository = OfflineDeviceStateRepository(MemoryDeviceStore())
        assertThat(repository.current().onboarding.completed).isFalse()
        repository.update { it.copy(onboarding = it.onboarding.copy(completed = true)) }
        assertThat(repository.state.first().onboarding.completed).isTrue()
    }
}
