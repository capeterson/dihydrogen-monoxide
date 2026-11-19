package casa.peterson.dhmo.feature.settings.export

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.testing.renderScreenVariants
import org.junit.Test
import org.junit.runner.RunWith

// The busy states are not captured: an indeterminate progress indicator never lets Compose go idle.
@RunWith(AndroidJUnit4::class)
class ExportRenderTest {
    @Test
    fun idle() = renderScreenVariants("SettingsExport") { ExportContent(ExportUiState(), onBack = {}, onExport = {}) }
}
