package casa.peterson.dhmo.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import casa.peterson.dhmo.core.database.entity.IntakeEntryEntity
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface IntakeDao {
    @Upsert suspend fun upsert(entry: IntakeEntryEntity)

    @Upsert suspend fun upsertAll(entries: List<IntakeEntryEntity>)

    @Query("SELECT * FROM intake_entry WHERE id = :id")
    suspend fun getById(id: UUID): IntakeEntryEntity?

    @Query("SELECT * FROM intake_entry WHERE hc_record_id = :hcRecordId")
    suspend fun getByHcRecordId(hcRecordId: String): IntakeEntryEntity?

    @Query("SELECT * FROM intake_entry WHERE hydration_day = :day AND deleted_at IS NULL ORDER BY instant ASC")
    fun observeDay(day: LocalDate): Flow<List<IntakeEntryEntity>>

    @Query("SELECT * FROM intake_entry WHERE hydration_day BETWEEN :from AND :to AND deleted_at IS NULL ORDER BY instant ASC")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<IntakeEntryEntity>>

    @Query("SELECT * FROM intake_entry WHERE hydration_day BETWEEN :from AND :to AND deleted_at IS NULL ORDER BY instant ASC")
    suspend fun getRange(from: LocalDate, to: LocalDate): List<IntakeEntryEntity>

    @Query("SELECT * FROM intake_entry WHERE deleted_at IS NULL ORDER BY hydration_day DESC, instant DESC, id DESC")
    fun pagingSource(): PagingSource<Int, IntakeEntryEntity> // History list

    @Query("SELECT COALESCE(SUM(volume_ml), 0) FROM intake_entry WHERE hydration_day = :day AND deleted_at IS NULL")
    suspend fun totalForDay(day: LocalDate): Int

    @Query("SELECT MAX(instant) FROM intake_entry WHERE hydration_day = :day AND deleted_at IS NULL")
    suspend fun lastDrinkInstant(day: LocalDate): Instant?

    @Query("UPDATE intake_entry SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: UUID, now: Instant)

    @Query("UPDATE intake_entry SET deleted_at = NULL, updated_at = :now WHERE id = :id")
    suspend fun restore(id: UUID, now: Instant) // Undo

    @Query("UPDATE intake_entry SET hc_record_id = :hcRecordId WHERE id = :id")
    suspend fun setHcRecordId(id: UUID, hcRecordId: String?) // does not bump updated_at

    @Query("SELECT * FROM intake_entry WHERE hydration_day >= :fromDay")
    suspend fun entriesFromDay(fromDay: LocalDate): List<IntakeEntryEntity> // includes tombstones

    @Query("UPDATE intake_entry SET hydration_day = :day WHERE id = :id")
    suspend fun updateHydrationDay(id: UUID, day: LocalDate)

    @Query("SELECT * FROM intake_entry WHERE source = 'HEALTH_CONNECT_IMPORT' AND instant BETWEEN :from AND :to")
    suspend fun importsBetween(from: Instant, to: Instant): List<IntakeEntryEntity>

    @Query(
        "SELECT * FROM intake_entry WHERE source = 'HEALTH_CONNECT_IMPORT' AND hc_data_origin = :packageName " +
            "AND deleted_at IS NULL AND hc_adopted = 0",
    )
    suspend fun liveImportsFrom(packageName: String): List<IntakeEntryEntity>

    @Query("SELECT * FROM intake_entry") // backup: everything incl. tombstones
    suspend fun getAllIncludingDeleted(): List<IntakeEntryEntity>

    @Query("SELECT COUNT(*) FROM intake_entry WHERE deleted_at IS NULL")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM intake_entry")
    suspend fun deleteAll() // restore (replace) only
}
