package casa.peterson.dhmo.sync.healthconnect

import casa.peterson.dhmo.core.data.repository.EntryChangeListener
import casa.peterson.dhmo.core.data.repository.HealthConnectController
import casa.peterson.dhmo.sync.healthconnect.engine.DefaultHealthConnectClientProvider
import casa.peterson.dhmo.sync.healthconnect.engine.HealthConnectClientProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class HealthConnectModule {
    @Binds
    @Singleton
    abstract fun healthConnectController(impl: HealthConnectControllerImpl): HealthConnectController

    @Binds
    abstract fun clientProvider(impl: DefaultHealthConnectClientProvider): HealthConnectClientProvider

    @Binds
    @IntoSet
    abstract fun entryListener(impl: HcEntryListener): EntryChangeListener
}
