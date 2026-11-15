package casa.peterson.dhmo.sync.drive

import casa.peterson.dhmo.core.data.repository.DriveBackupController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DriveModule {
    @Binds
    @Singleton
    abstract fun driveBackupController(impl: NoOpDriveBackupController): DriveBackupController
}
