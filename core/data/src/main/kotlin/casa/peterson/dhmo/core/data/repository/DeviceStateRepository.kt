package casa.peterson.dhmo.core.data.repository

import casa.peterson.dhmo.core.model.settings.DeviceState
import kotlinx.coroutines.flow.Flow

interface DeviceStateRepository {
    val state: Flow<DeviceState>

    suspend fun current(): DeviceState

    suspend fun update(transform: (DeviceState) -> DeviceState)
}
