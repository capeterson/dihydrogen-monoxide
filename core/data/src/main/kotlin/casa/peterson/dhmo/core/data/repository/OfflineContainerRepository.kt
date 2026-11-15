package casa.peterson.dhmo.core.data.repository

import casa.peterson.dhmo.core.model.Container
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Stub: the Room-backed implementation arrives with T4. */
class OfflineContainerRepository @Inject constructor() : ContainerRepository {
    override fun observeAll(): Flow<List<Container>> = TODO("T4")

    override suspend fun getAll(): List<Container> = TODO("T4")

    override suspend fun upsert(container: Container) = TODO("T4")

    override suspend fun delete(id: UUID) = TODO("T4")

    override suspend fun reorder(idsInOrder: List<UUID>) = TODO("T4")

    override suspend fun seedDefaultsIfEmpty(units: VolumeUnit) = TODO("T4")
}
