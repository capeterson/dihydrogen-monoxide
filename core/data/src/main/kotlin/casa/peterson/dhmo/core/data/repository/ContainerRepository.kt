package casa.peterson.dhmo.core.data.repository

import casa.peterson.dhmo.core.model.Container
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface ContainerRepository {
    fun observeAll(): Flow<List<Container>>

    suspend fun getAll(): List<Container>

    suspend fun upsert(container: Container)

    suspend fun delete(id: UUID)

    suspend fun reorder(idsInOrder: List<UUID>)

    suspend fun seedDefaultsIfEmpty(units: VolumeUnit)
}
