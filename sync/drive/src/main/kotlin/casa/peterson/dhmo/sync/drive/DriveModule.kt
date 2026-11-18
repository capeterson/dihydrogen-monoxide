package casa.peterson.dhmo.sync.drive

import casa.peterson.dhmo.core.data.repository.DriveBackupController
import casa.peterson.dhmo.sync.drive.api.DriveEndpoints
import casa.peterson.dhmo.sync.drive.api.DriveTokenSource
import casa.peterson.dhmo.sync.drive.auth.AuthorizationTokenSource
import casa.peterson.dhmo.sync.drive.auth.DriveAuth
import casa.peterson.dhmo.sync.drive.auth.GoogleAuthManager
import casa.peterson.dhmo.sync.drive.crypto.AndroidKeystoreWrappingKey
import casa.peterson.dhmo.sync.drive.crypto.WrappingKeyProvider
import casa.peterson.dhmo.sync.drive.work.DriveScheduler
import casa.peterson.dhmo.sync.drive.work.DriveWorkScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DriveModule {
    @Binds
    @Singleton
    abstract fun driveBackupController(impl: DriveBackupControllerImpl): DriveBackupController

    @Binds
    abstract fun auth(impl: GoogleAuthManager): DriveAuth

    @Binds
    abstract fun scheduler(impl: DriveWorkScheduler): DriveScheduler

    @Binds
    abstract fun tokenSource(impl: AuthorizationTokenSource): DriveTokenSource

    @Binds
    abstract fun wrappingKey(impl: AndroidKeystoreWrappingKey): WrappingKeyProvider

    companion object {
        @Provides
        fun endpoints(): DriveEndpoints = DriveEndpoints()
    }
}
