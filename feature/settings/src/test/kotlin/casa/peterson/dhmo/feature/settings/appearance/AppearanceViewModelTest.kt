package casa.peterson.dhmo.feature.settings.appearance

import casa.peterson.dhmo.core.model.settings.ThemeMode
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val settings = FakeSettingsRepository()
    private val model by lazy { AppearanceViewModel(settings) }

    @Test
    fun themeChangesAreSaved() = runTest {
        model.theme.launchIn(backgroundScope)
        model.setMode(ThemeMode.DARK)
        model.setDynamicColor(true)
        model.setAnimationsEnabled(false)
        advanceUntilIdle()
        assertThat(settings.current().theme.mode).isEqualTo(ThemeMode.DARK)
        assertThat(settings.current().theme.dynamicColor).isTrue()
        assertThat(model.theme.value.mode).isEqualTo(ThemeMode.DARK)
        assertThat(settings.current().theme.animationsEnabled).isFalse()
        assertThat(model.theme.value.animationsEnabled).isFalse()
    }
}
