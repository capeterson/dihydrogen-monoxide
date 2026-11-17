package casa.peterson.dhmo.sync.wear

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class WearModule {
    @Binds
    abstract fun publisher(impl: DataLayerWearStatePublisher): WearStatePublisher
}
