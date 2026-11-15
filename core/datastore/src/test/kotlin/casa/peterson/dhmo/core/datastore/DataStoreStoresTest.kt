package casa.peterson.dhmo.core.datastore

import casa.peterson.dhmo.core.model.settings.VolumeUnit
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreStoresTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After fun tearDown() = scope.cancel()

    @Test
    fun `settings update persists and sanitises`() = runBlocking {
        val file = folder.newFolder().resolve("settings.json")
        val store = DataStoreSettingsStore(settingsDataStore(file, scope))
        assertThat(store.settings.first().units).isEqualTo(VolumeUnit.ML)
        val updated = store.update { it.copy(units = VolumeUnit.L, dayStartMinutes = 9999) }
        assertThat(updated.units).isEqualTo(VolumeUnit.L)
        assertThat(updated.dayStartMinutes).isEqualTo(1439)
        assertThat(file.readText()).contains("\"units\"")
    }

    @Test
    fun `corrupt settings file falls back to defaults`() = runBlocking {
        val file = folder.newFolder().resolve("settings.json")
        file.writeText("<<garbage>>")
        val store = DataStoreSettingsStore(settingsDataStore(file, scope))
        assertThat(store.settings.first().units).isEqualTo(VolumeUnit.ML)
    }

    @Test
    fun `device state update`() = runBlocking {
        val store = DataStoreDeviceStateStore(deviceStateDataStore(folder.newFolder().resolve("device_state.json"), scope))
        store.update { it.copy(onboarding = it.onboarding.copy(completed = true, lastStep = "UnitsRoute")) }
        assertThat(store.state.first().onboarding.completed).isTrue()
        assertThat(store.state.first().onboarding.lastStep).isEqualTo("UnitsRoute")
    }
}
