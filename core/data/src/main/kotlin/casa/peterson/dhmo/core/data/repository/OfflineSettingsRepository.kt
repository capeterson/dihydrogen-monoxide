package casa.peterson.dhmo.core.data.repository

import casa.peterson.dhmo.core.datastore.SettingsStore
import casa.peterson.dhmo.core.model.settings.AppSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class OfflineSettingsRepository
@Inject
constructor(private val store: SettingsStore) : SettingsRepository {
    override val settings: Flow<AppSettings> = store.settings

    override suspend fun current(): AppSettings = store.settings.first()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.update(transform)
    }
}
