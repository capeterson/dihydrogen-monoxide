package casa.peterson.dhmo.feature.settings.export

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.testing.captureScreenVariants
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

// The busy states are not captured: an indeterminate progress indicator never lets Compose go idle.
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExportScreenshotTest {
    @Test
    fun idle() = captureScreenVariants("SettingsExport") { ExportContent(ExportUiState(), onBack = {}, onExport = {}) }
}
