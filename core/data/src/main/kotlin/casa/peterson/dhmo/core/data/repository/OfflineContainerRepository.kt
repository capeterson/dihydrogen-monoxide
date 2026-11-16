package casa.peterson.dhmo.core.data.repository

import casa.peterson.dhmo.core.data.mapper.toEntity
import casa.peterson.dhmo.core.data.mapper.toModel
import casa.peterson.dhmo.core.database.TransactionRunner
import casa.peterson.dhmo.core.database.dao.ContainerDao
import casa.peterson.dhmo.core.model.Container
import casa.peterson.dhmo.core.model.ContainerIcon
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room-backed [ContainerRepository]; seeding uses fixed ids so two devices merge cleanly (§4.3). */
class OfflineContainerRepository
@Inject
constructor(
    private val dao: ContainerDao,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) : ContainerRepository {
    override fun observeAll(): Flow<List<Container>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun getAll(): List<Container> = dao.getAll().map { it.toModel() }

    override suspend fun upsert(container: Container) {
        dao.upsert(container.copy(updatedAt = clock.instant()).toEntity())
    }

    override suspend fun delete(id: UUID) {
        dao.softDelete(id, clock.instant())
    }

    /** Listed ids take positions 0..n-1 in the given order; live containers not listed follow in their old order. */
    override suspend fun reorder(idsInOrder: List<UUID>) {
        val now = clock.instant()
        transactions.run {
            val live = dao.getAll()
            val byId = live.associateBy { it.id }
            val requested = idsInOrder.toSet()
            val listed = requested.mapNotNull(byId::get)
            val rest = live.filter { row -> row.id !in requested }
            (listed + rest).forEachIndexed { index, row ->
                if (row.sortOrder != index) dao.upsert(row.copy(sortOrder = index, updatedAt = now))
            }
        }
    }

    override suspend fun seedDefaultsIfEmpty(units: VolumeUnit) {
        val now = clock.instant()
        transactions.run {
            if (dao.countIncludingDeleted() == 0) {
                val volumes = if (units.isOunces) OUNCE_VOLUMES else MILLILITRE_VOLUMES
                dao.upsertAll(
                    SEEDS.mapIndexed { index, seed ->
                        Container(seed.id, seed.name, volumes[index], seed.icon, index, now).toEntity()
                    },
                )
            }
        }
    }

    private val VolumeUnit.isOunces: Boolean get() = this == VolumeUnit.US_FL_OZ || this == VolumeUnit.IMP_FL_OZ

    private class Seed(val id: UUID, val name: String, val icon: ContainerIcon)

    private companion object {
        val SEEDS = listOf(
            Seed(UUID.fromString("00000000-0000-4000-8000-000000000001"), "Glass", ContainerIcon.GLASS),
            Seed(UUID.fromString("00000000-0000-4000-8000-000000000002"), "Bottle", ContainerIcon.BOTTLE),
            Seed(UUID.fromString("00000000-0000-4000-8000-000000000003"), "Large bottle", ContainerIcon.LARGE_BOTTLE),
        )
        val MILLILITRE_VOLUMES = listOf(250, 500, 750)

        /** 8 / 16 / 24 fl oz converted to millilitres (§4.3). */
        val OUNCE_VOLUMES = listOf(237, 473, 710)
    }
}
