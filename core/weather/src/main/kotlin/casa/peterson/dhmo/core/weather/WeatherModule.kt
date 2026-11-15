package casa.peterson.dhmo.core.weather

import casa.peterson.dhmo.core.data.repository.WeatherController
import casa.peterson.dhmo.core.data.repository.WeatherProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WeatherModule {
    @Binds
    @Singleton
    abstract fun weatherController(impl: NoOpWeatherController): WeatherController

    @Binds
    @Singleton
    abstract fun weatherProvider(impl: NoOpWeatherProvider): WeatherProvider
}
