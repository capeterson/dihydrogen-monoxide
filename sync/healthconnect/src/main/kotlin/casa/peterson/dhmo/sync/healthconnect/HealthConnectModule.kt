package casa.peterson.dhmo.sync.healthconnect

import casa.peterson.dhmo.core.data.repository.HealthConnectController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class HealthConnectModule {
    @Binds
    @Singleton
    abstract fun healthConnectController(impl: NoOpHealthConnectController): HealthConnectController
}
