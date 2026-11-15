package casa.peterson.dhmo.core.data.repository

import casa.peterson.dhmo.core.datastore.DeviceStateStore
import casa.peterson.dhmo.core.model.settings.DeviceState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class OfflineDeviceStateRepository
@Inject
constructor(private val store: DeviceStateStore) : DeviceStateRepository {
    override val state: Flow<DeviceState> = store.state

    override suspend fun current(): DeviceState = store.state.first()

    override suspend fun update(transform: (DeviceState) -> DeviceState) {
        store.update(transform)
    }
}
