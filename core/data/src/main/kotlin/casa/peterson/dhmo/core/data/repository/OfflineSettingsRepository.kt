package casa.peterson.dhmo.core.data.repository

import android.util.Log
import casa.peterson.dhmo.core.common.suspendRunCatching
import casa.peterson.dhmo.core.datastore.SettingsStore
import casa.peterson.dhmo.core.model.settings.AppSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class OfflineSettingsRepository
@Inject
constructor(private val store: SettingsStore, private val widgets: WidgetUpdater) :
    SettingsRepository {
    override val settings: Flow<AppSettings> = store.settings

    override suspend fun current(): AppSettings = store.settings.first()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.update(transform)
        // Units, wake window and goal overrides all change what the widget shows.
        suspendRunCatching { widgets.updateAll() }.onFailure { Log.w(TAG, "Widget refresh failed", it) }
    }

    private companion object {
        const val TAG = "SettingsRepository"
    }
}
