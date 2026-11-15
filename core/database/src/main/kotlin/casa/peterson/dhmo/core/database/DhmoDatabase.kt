package casa.peterson.dhmo.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import casa.peterson.dhmo.core.database.dao.ActivityDao
import casa.peterson.dhmo.core.database.dao.ContainerDao
import casa.peterson.dhmo.core.database.dao.DailyTotalDao
import casa.peterson.dhmo.core.database.dao.GoalDao
import casa.peterson.dhmo.core.database.dao.HcSyncDao
import casa.peterson.dhmo.core.database.dao.HealthDailyDao
import casa.peterson.dhmo.core.database.dao.IntakeDao
import casa.peterson.dhmo.core.database.dao.ProfileDao
import casa.peterson.dhmo.core.database.dao.WeatherDao
import casa.peterson.dhmo.core.database.entity.ActivityAdjustmentEntity
import casa.peterson.dhmo.core.database.entity.ContainerEntity
import casa.peterson.dhmo.core.database.entity.DailyGoalEntity
import casa.peterson.dhmo.core.database.entity.DailyTotalView
import casa.peterson.dhmo.core.database.entity.DayOverrideEntity
import casa.peterson.dhmo.core.database.entity.HcPendingOpEntity
import casa.peterson.dhmo.core.database.entity.HealthDailyEntity
import casa.peterson.dhmo.core.database.entity.IntakeEntryEntity
import casa.peterson.dhmo.core.database.entity.ProfileSnapshotEntity
import casa.peterson.dhmo.core.database.entity.SyncStateEntity
import casa.peterson.dhmo.core.database.entity.WeatherDayEntity

@Database(
    entities = [
        IntakeEntryEntity::class,
        ContainerEntity::class,
        ProfileSnapshotEntity::class,
        DailyGoalEntity::class,
        DayOverrideEntity::class,
        ActivityAdjustmentEntity::class,
        WeatherDayEntity::class,
        HealthDailyEntity::class,
        HcPendingOpEntity::class,
        SyncStateEntity::class,
    ],
    views = [DailyTotalView::class],
    version = DhmoDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class DhmoDatabase : RoomDatabase() {
    abstract fun intakeDao(): IntakeDao

    abstract fun dailyTotalDao(): DailyTotalDao

    abstract fun containerDao(): ContainerDao

    abstract fun profileDao(): ProfileDao

    abstract fun goalDao(): GoalDao

    abstract fun activityDao(): ActivityDao

    abstract fun weatherDao(): WeatherDao

    abstract fun healthDailyDao(): HealthDailyDao

    abstract fun hcSyncDao(): HcSyncDao

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "dhmo.db"
    }
}
