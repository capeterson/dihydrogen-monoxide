package casa.peterson.dhmo.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import casa.peterson.dhmo.core.common.ApplicationScope
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.settings.DeviceState
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

/** `files/datastore/settings.json` is backed up; `files/datastore/device_state.json` never is. */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun settingsStore(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope): SettingsStore =
        DataStoreSettingsStore(settingsDataStore(datastoreFile(context, SETTINGS_FILE), scope))

    @Provides
    @Singleton
    fun deviceStateStore(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope): DeviceStateStore =
        DataStoreDeviceStateStore(deviceStateDataStore(datastoreFile(context, DEVICE_STATE_FILE), scope))

    private fun datastoreFile(context: Context, name: String): File = File(context.filesDir, "datastore/$name")

    internal const val SETTINGS_FILE = "settings.json"
    internal const val DEVICE_STATE_FILE = "device_state.json"
}

internal fun settingsDataStore(file: File, scope: CoroutineScope): DataStore<AppSettings> = DataStoreFactory.create(
    serializer = AppSettingsSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { AppSettings() },
    scope = scope,
    produceFile = { file },
)

internal fun deviceStateDataStore(file: File, scope: CoroutineScope): DataStore<DeviceState> = DataStoreFactory.create(
    serializer = DeviceStateSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { DeviceState() },
    scope = scope,
    produceFile = { file },
)
