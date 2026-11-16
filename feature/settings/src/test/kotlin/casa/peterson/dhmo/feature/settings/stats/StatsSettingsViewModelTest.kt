package casa.peterson.dhmo.feature.settings.stats

import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatsSettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val settings = FakeSettingsRepository()
    private val model by lazy { StatsSettingsViewModel(settings) }

    @Test
    fun togglesAreSaved() = runTest {
        model.setIncludeNoLogDays(true)
        model.setWeeklyStreakFreeze(true)
        advanceUntilIdle()
        assertThat(settings.current().stats.includeNoLogDays).isTrue()
        assertThat(settings.current().stats.weeklyStreakFreeze).isTrue()
    }
}
