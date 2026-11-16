package casa.peterson.dhmo.core.designsystem.screenshot

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.designsystem.component.PlaceholderScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaceholderLightScreenshotTest {
    @Test
    fun capture() = captureStable("PlaceholderScreen", "light") { PlaceholderScreen(title = "Today") }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaceholderDarkScreenshotTest {
    @Test
    fun capture() = captureStable("PlaceholderScreen", "dark") { PlaceholderScreen(title = "Today") }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaceholderLargeFontScreenshotTest {
    @Test
    fun capture() = captureStable("PlaceholderScreen", "largefont") { PlaceholderScreen(title = "Today") }
}
