package casa.peterson.dhmo.core.data.backup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {
    @Binds
    abstract fun backupEnvironment(impl: AndroidBackupEnvironment): BackupEnvironment

    @Binds
    abstract fun afterRestore(impl: DefaultAfterRestore): AfterRestore
}
