package casa.peterson.dhmo.core.data.di

import casa.peterson.dhmo.core.data.backup.OfflineBackupRepository
import casa.peterson.dhmo.core.data.repository.ActivityRepository
import casa.peterson.dhmo.core.data.repository.BackupRepository
import casa.peterson.dhmo.core.data.repository.ContainerRepository
import casa.peterson.dhmo.core.data.repository.DeviceStateRepository
import casa.peterson.dhmo.core.data.repository.EntryChangeListener
import casa.peterson.dhmo.core.data.repository.GoalRepository
import casa.peterson.dhmo.core.data.repository.IntakeRepository
import casa.peterson.dhmo.core.data.repository.OfflineActivityRepository
import casa.peterson.dhmo.core.data.repository.OfflineContainerRepository
import casa.peterson.dhmo.core.data.repository.OfflineDeviceStateRepository
import casa.peterson.dhmo.core.data.repository.OfflineGoalRepository
import casa.peterson.dhmo.core.data.repository.OfflineIntakeRepository
import casa.peterson.dhmo.core.data.repository.OfflineProfileRepository
import casa.peterson.dhmo.core.data.repository.OfflineSettingsRepository
import casa.peterson.dhmo.core.data.repository.OfflineWeatherRepository
import casa.peterson.dhmo.core.data.repository.ProfileRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.repository.WeatherRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun intake(impl: OfflineIntakeRepository): IntakeRepository

    @Binds @Singleton
    abstract fun container(impl: OfflineContainerRepository): ContainerRepository

    @Binds @Singleton
    abstract fun profile(impl: OfflineProfileRepository): ProfileRepository

    @Binds @Singleton
    abstract fun goal(impl: OfflineGoalRepository): GoalRepository

    @Binds @Singleton
    abstract fun activity(impl: OfflineActivityRepository): ActivityRepository

    @Binds @Singleton
    abstract fun weather(impl: OfflineWeatherRepository): WeatherRepository

    @Binds @Singleton
    abstract fun settings(impl: OfflineSettingsRepository): SettingsRepository

    @Binds @Singleton
    abstract fun deviceState(impl: OfflineDeviceStateRepository): DeviceStateRepository

    @Binds @Singleton
    abstract fun backup(impl: OfflineBackupRepository): BackupRepository

    /** Keeps the empty set valid until other modules contribute `@IntoSet` listeners (§6.4). */
    @Multibinds
    abstract fun entryChangeListeners(): Set<EntryChangeListener>
}
