package casa.peterson.dhmo.core.designsystem.screenshot

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.designsystem.component.PlaceholderScreen
import casa.peterson.dhmo.core.testing.captureScreenVariants
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaceholderScreenScreenshotTest {
    @Test
    fun placeholderScreen() {
        captureScreenVariants("PlaceholderScreen") { PlaceholderScreen(title = "Today") }
    }
}
