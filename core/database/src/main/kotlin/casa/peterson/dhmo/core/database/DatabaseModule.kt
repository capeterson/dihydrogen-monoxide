package casa.peterson.dhmo.core.database

import android.content.Context
import androidx.room.Room
import casa.peterson.dhmo.core.database.dao.ActivityDao
import casa.peterson.dhmo.core.database.dao.ContainerDao
import casa.peterson.dhmo.core.database.dao.DailyTotalDao
import casa.peterson.dhmo.core.database.dao.GoalDao
import casa.peterson.dhmo.core.database.dao.HcSyncDao
import casa.peterson.dhmo.core.database.dao.HealthDailyDao
import casa.peterson.dhmo.core.database.dao.IntakeDao
import casa.peterson.dhmo.core.database.dao.ProfileDao
import casa.peterson.dhmo.core.database.dao.WeatherDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): DhmoDatabase =
        Room.databaseBuilder(context, DhmoDatabase::class.java, DhmoDatabase.FILE_NAME).build()

    @Provides fun intakeDao(db: DhmoDatabase): IntakeDao = db.intakeDao()

    @Provides fun dailyTotalDao(db: DhmoDatabase): DailyTotalDao = db.dailyTotalDao()

    @Provides fun containerDao(db: DhmoDatabase): ContainerDao = db.containerDao()

    @Provides fun profileDao(db: DhmoDatabase): ProfileDao = db.profileDao()

    @Provides fun goalDao(db: DhmoDatabase): GoalDao = db.goalDao()

    @Provides fun activityDao(db: DhmoDatabase): ActivityDao = db.activityDao()

    @Provides fun weatherDao(db: DhmoDatabase): WeatherDao = db.weatherDao()

    @Provides fun healthDailyDao(db: DhmoDatabase): HealthDailyDao = db.healthDailyDao()

    @Provides fun hcSyncDao(db: DhmoDatabase): HcSyncDao = db.hcSyncDao()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class TransactionModule {
    @Binds
    abstract fun transactionRunner(impl: RoomTransactionRunner): TransactionRunner
}
