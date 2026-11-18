package casa.peterson.dhmo.feature.settings.weather

import casa.peterson.dhmo.core.data.repository.GeoPlace
import casa.peterson.dhmo.core.data.repository.WeatherResult
import casa.peterson.dhmo.core.model.settings.ManualCity
import casa.peterson.dhmo.core.model.settings.WeatherMode
import casa.peterson.dhmo.core.testing.FakeDeviceLocationSource
import casa.peterson.dhmo.core.testing.FakeDeviceStateRepository
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.FakeWeatherController
import casa.peterson.dhmo.core.testing.FakeWeatherProvider
import casa.peterson.dhmo.core.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherSettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val location = FakeDeviceLocationSource()
    private val controller = FakeWeatherController()
    private val viewModel by lazy { WeatherSettingsViewModel(settings, FakeDeviceStateRepository(), controller, location) }

    @Test
    fun `a granted permission turns device mode on`() = runTest {
        viewModel.onLocationPermissionResult(true)
        assertThat(settings.current().weather.mode).isEqualTo(WeatherMode.DEVICE_LOCATION)
    }

    @Test
    fun `a denied permission keeps the mode off and says so`() = runTest {
        viewModel.onLocationPermissionResult(false)
        assertThat(settings.current().weather.mode).isEqualTo(WeatherMode.OFF)
        val collector = backgroundScope.launchCollect(viewModel)
        assertThat(viewModel.state.value.permissionDenied).isTrue()
        collector.cancel()
    }

    @Test
    fun `city mode needs a chosen city first`() = runTest {
        var opened = 0
        viewModel.useCity { opened++ }
        assertThat(opened).isEqualTo(1)
        assertThat(settings.current().weather.mode).isEqualTo(WeatherMode.OFF)
        settings.update { it.copy(weather = it.weather.copy(manualCity = ManualCity("Berlin", null, "DE", 525, 134))) }
        viewModel.useCity { opened++ }
        assertThat(opened).isEqualTo(1)
        assertThat(settings.current().weather.mode).isEqualTo(WeatherMode.MANUAL_CITY)
    }

    @Test
    fun `off stops weather`() = runTest {
        settings.update { it.copy(weather = it.weather.copy(mode = WeatherMode.DEVICE_LOCATION)) }
        viewModel.setOff()
        assertThat(settings.current().weather.mode).isEqualTo(WeatherMode.OFF)
    }

    @Test
    fun `an unsupported phone is reported`() = runTest {
        location.supported = false
        val collector = backgroundScope.launchCollect(viewModel)
        assertThat(viewModel.state.value.deviceSupported).isFalse()
        collector.cancel()
    }

    @Test
    fun `refresh reads the location in device mode then fetches`() = runTest {
        settings.update { it.copy(weather = it.weather.copy(mode = WeatherMode.DEVICE_LOCATION)) }
        val collector = backgroundScope.launchCollect(viewModel)
        viewModel.refresh()
        assertThat(location.refreshCount).isEqualTo(1)
        assertThat(controller.refreshNowCount).isEqualTo(1)
        collector.cancel()
    }

    @Test
    fun `choosing a city stores only rounded coordinates and switches to city mode`() = runTest {
        val search = CitySearchViewModel(FakeWeatherProvider(), settings, controller, kotlinx.coroutines.CoroutineScope(main.dispatcher))
        var done = false
        search.choose(GeoPlace("Berlin", "State of Berlin", "Germany", "DE", 52.52437, 13.41053)) { done = true }
        runCurrent()
        val city = settings.current().weather.manualCity!!
        assertThat(city.latE1).isEqualTo(525)
        assertThat(city.lonE1).isEqualTo(134)
        assertThat(settings.current().weather.mode).isEqualTo(WeatherMode.MANUAL_CITY)
        assertThat(done).isTrue()
    }

    @Test
    fun `search is debounced and ignores queries under two characters`() = runTest {
        val provider = FakeWeatherProvider(places = WeatherResult.Success(listOf(GeoPlace("Berlin", null, "Germany", "DE", 52.5, 13.4))))
        val search = CitySearchViewModel(provider, settings, controller, kotlinx.coroutines.CoroutineScope(main.dispatcher))
        search.onQueryChange("B")
        advanceTimeBy(500)
        runCurrent()
        assertThat(search.state.value).isEqualTo(CitySearchState.Idle)
        search.onQueryChange("Be")
        search.onQueryChange("Ber")
        advanceTimeBy(300)
        assertThat(search.state.value).isEqualTo(CitySearchState.Idle)
        advanceTimeBy(200)
        runCurrent()
        assertThat((search.state.value as CitySearchState.Results).places.single().name).isEqualTo("Berlin")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun kotlinx.coroutines.CoroutineScope.launchCollect(viewModel: WeatherSettingsViewModel) =
    launch(kotlinx.coroutines.test.UnconfinedTestDispatcher()) { viewModel.state.collect { } }
