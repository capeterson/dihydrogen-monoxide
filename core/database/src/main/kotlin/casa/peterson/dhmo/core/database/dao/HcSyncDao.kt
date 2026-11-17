package casa.peterson.dhmo.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import casa.peterson.dhmo.core.database.entity.HcPendingOpEntity
import casa.peterson.dhmo.core.database.entity.SyncStateEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface HcSyncDao {
    @Insert suspend fun insertOp(op: HcPendingOpEntity): Long

    @Query("DELETE FROM hc_pending_op WHERE entry_id = :entryId")
    suspend fun deleteOpsFor(entryId: UUID)

    @Transaction
    suspend fun enqueue(op: HcPendingOpEntity) {
        deleteOpsFor(op.entryId)
        insertOp(op)
    }

    @Query("SELECT * FROM hc_pending_op ORDER BY id ASC LIMIT :limit")
    suspend fun nextOps(limit: Int): List<HcPendingOpEntity>

    @Query("DELETE FROM hc_pending_op WHERE id IN (:ids)")
    suspend fun deleteOps(ids: List<Long>)

    @Query("UPDATE hc_pending_op SET attempts = attempts + 1, last_error = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)

    @Query("SELECT COUNT(*) FROM hc_pending_op")
    fun observePendingCount(): Flow<Int>

    @Query("DELETE FROM hc_pending_op")
    suspend fun clearOps()

    @Query("SELECT value FROM sync_state WHERE `key` = :key")
    suspend fun getState(key: String): String?

    @Upsert suspend fun putState(state: SyncStateEntity)

    @Query("DELETE FROM sync_state WHERE `key` = :key")
    suspend fun deleteState(key: String)

    @Query("DELETE FROM sync_state")
    suspend fun clearState() // restore (replace) only

    @Query("SELECT * FROM sync_state")
    suspend fun allStates(): List<SyncStateEntity>

    @Query("SELECT * FROM hc_pending_op ORDER BY id ASC")
    suspend fun allOps(): List<HcPendingOpEntity>
}
