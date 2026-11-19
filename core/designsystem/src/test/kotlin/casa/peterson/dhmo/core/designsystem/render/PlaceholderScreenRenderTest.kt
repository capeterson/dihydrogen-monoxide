package casa.peterson.dhmo.core.designsystem.render

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.designsystem.component.PlaceholderScreen
import casa.peterson.dhmo.core.testing.renderScreenVariants
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaceholderScreenRenderTest {
    @Test
    fun rendersInLightAndDarkLargeFont() = renderScreenVariants("PlaceholderScreen") { PlaceholderScreen(title = "Today") }
}
