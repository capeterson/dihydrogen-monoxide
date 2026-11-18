package casa.peterson.dhmo.widget

import casa.peterson.dhmo.core.data.repository.EntryChangeListener
import casa.peterson.dhmo.core.data.repository.WidgetUpdater
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WidgetModule {
    @Binds
    @Singleton
    abstract fun widgetUpdater(impl: GlanceWidgetUpdater): WidgetUpdater

    @Binds
    @IntoSet
    abstract fun entryListener(impl: WidgetEntryListener): EntryChangeListener
}
