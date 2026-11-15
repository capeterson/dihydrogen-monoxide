package casa.peterson.dhmo.core.datastore

import androidx.datastore.core.DataStore
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.settings.DeviceState
import kotlinx.coroutines.flow.Flow

/** User settings (backed up). */
interface SettingsStore {
    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings
}

/** Device state (never backed up). */
interface DeviceStateStore {
    val state: Flow<DeviceState>

    suspend fun update(transform: (DeviceState) -> DeviceState): DeviceState
}

internal class DataStoreSettingsStore(private val dataStore: DataStore<AppSettings>) : SettingsStore {
    override val settings: Flow<AppSettings> = dataStore.data

    override suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings =
        dataStore.updateData { SettingsValidator.sanitize(transform(it)) }
}

internal class DataStoreDeviceStateStore(private val dataStore: DataStore<DeviceState>) : DeviceStateStore {
    override val state: Flow<DeviceState> = dataStore.data

    override suspend fun update(transform: (DeviceState) -> DeviceState): DeviceState = dataStore.updateData(transform)
}
