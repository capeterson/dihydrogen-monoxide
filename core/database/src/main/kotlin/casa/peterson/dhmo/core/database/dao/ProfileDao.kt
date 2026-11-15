package casa.peterson.dhmo.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import casa.peterson.dhmo.core.database.entity.ProfileSnapshotEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: ProfileSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(snapshots: List<ProfileSnapshotEntity>)

    @Query("DELETE FROM profile_snapshot WHERE effective_from = :day AND source = 'MANUAL'")
    suspend fun deleteManualOn(day: LocalDate)

    @Query("SELECT * FROM profile_snapshot ORDER BY effective_from DESC, created_at DESC LIMIT 1")
    fun observeLatest(): Flow<ProfileSnapshotEntity?>

    @Query("SELECT * FROM profile_snapshot ORDER BY effective_from ASC, created_at ASC")
    fun observeAll(): Flow<List<ProfileSnapshotEntity>>

    @Query("SELECT * FROM profile_snapshot ORDER BY effective_from ASC, created_at ASC")
    suspend fun getAll(): List<ProfileSnapshotEntity> // resolution for a date is done in Kotlin (ProfileResolver)

    @Query("DELETE FROM profile_snapshot")
    suspend fun deleteAll()
}
