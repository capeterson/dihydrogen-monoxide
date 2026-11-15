package casa.peterson.dhmo.core.data.di

import casa.peterson.dhmo.core.common.ApplicationScope
import casa.peterson.dhmo.core.common.DhmoDispatcher
import casa.peterson.dhmo.core.common.Dispatcher
import casa.peterson.dhmo.core.common.ZoneProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object CommonModule {
    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun zoneProvider(): ZoneProvider = ZoneProvider { ZoneId.systemDefault() }

    @Provides
    @Dispatcher(DhmoDispatcher.IO)
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Dispatcher(DhmoDispatcher.Default)
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(@Dispatcher(DhmoDispatcher.Default) dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)
}
