package casa.peterson.dhmo.widget

import casa.peterson.dhmo.core.data.repository.WidgetUpdater
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WidgetModule {
    @Binds
    @Singleton
    abstract fun widgetUpdater(impl: NoOpWidgetUpdater): WidgetUpdater
}
