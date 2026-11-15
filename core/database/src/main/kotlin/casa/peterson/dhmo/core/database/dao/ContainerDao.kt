package casa.peterson.dhmo.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import casa.peterson.dhmo.core.database.entity.ContainerEntity
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface ContainerDao {
    @Query("SELECT * FROM container WHERE deleted_at IS NULL ORDER BY sort_order ASC, name ASC")
    fun observeAll(): Flow<List<ContainerEntity>>

    @Query("SELECT * FROM container WHERE deleted_at IS NULL ORDER BY sort_order ASC, name ASC")
    suspend fun getAll(): List<ContainerEntity>

    @Query("SELECT * FROM container WHERE id = :id")
    suspend fun getById(id: UUID): ContainerEntity?

    @Query("SELECT COUNT(*) FROM container")
    suspend fun countIncludingDeleted(): Int

    @Upsert suspend fun upsert(container: ContainerEntity)

    @Upsert suspend fun upsertAll(containers: List<ContainerEntity>)

    @Query("UPDATE container SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: UUID, now: Instant)

    @Query("SELECT * FROM container")
    suspend fun getAllIncludingDeleted(): List<ContainerEntity>

    @Query("DELETE FROM container")
    suspend fun deleteAll()
}
