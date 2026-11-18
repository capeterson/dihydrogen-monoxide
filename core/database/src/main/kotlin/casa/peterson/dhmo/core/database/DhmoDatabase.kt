package casa.peterson.dhmo.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RenameColumn
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
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
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = DhmoDatabase.GenderMigration::class)],
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

    /**
     * 1 to 2: `profile_snapshot.sex` became `gender`. The stored goal breakdowns are JSON that names the component
     * kind, so `BASE_SEX_FLOOR` becomes `BASE_GENDER_FLOOR` there too.
     */
    @RenameColumn(tableName = "profile_snapshot", fromColumnName = "sex", toColumnName = "gender")
    class GenderMigration : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE daily_goal SET breakdown_json = REPLACE(breakdown_json, 'BASE_SEX_FLOOR', 'BASE_GENDER_FLOOR')")
        }
    }

    companion object {
        const val VERSION = 2
        const val FILE_NAME = "dhmo.db"
    }
}
