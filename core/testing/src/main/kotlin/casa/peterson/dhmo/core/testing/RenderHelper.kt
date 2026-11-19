package casa.peterson.dhmo.core.testing

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.unit.Density
import casa.peterson.dhmo.core.designsystem.theme.DhmoTheme
import org.robolectric.RuntimeEnvironment

private const val LARGE_FONT_SCALE = 2f

/** 411 x 891 dp at 420 dpi (density 2.625), the size the app is designed against. */
private const val SCREEN_QUALIFIERS = "w411dp-h891dp-420dpi"

/**
 * Composes [content] on a phone-sized screen in the two combinations that break layouts: the light theme at normal font
 * size, and the dark theme at font scale 2.0. It fails if composition, measure or layout throws, or if the content
 * does not end up on screen.
 *
 * This is deliberately not a pixel comparison. Reference images fail on every intended visual change, bloat the
 * repository and need native graphics, which is flaky under Robolectric. Assert on behaviour in a view model or
 * interaction test; use this to prove a screen still renders in the worst-case theme and font size.
 *
 * Pass stateless `...Content(uiState, callbacks)` composables with fixed state. [name] only labels a failure.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooGenericExceptionCaught") // Any failure is rethrown, only labelled with the screen and variant.
fun renderScreenVariants(name: String, content: @Composable () -> Unit) {
    RuntimeEnvironment.setQualifiers(SCREEN_QUALIFIERS)
    listOf(Variant("light", darkTheme = false, fontScale = 1f), Variant("dark-largefont", darkTheme = true, fontScale = LARGE_FONT_SCALE))
        .forEach { variant ->
            try {
                runAndroidComposeUiTest<ComponentActivity> {
                    setContent {
                        val base = LocalDensity.current
                        CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = variant.fontScale)) {
                            DhmoTheme(darkTheme = variant.darkTheme, dynamicColor = false) {
                                Surface(modifier = Modifier.fillMaxSize()) { content() }
                            }
                        }
                    }
                    waitForIdle()
                    // A dialog or popup is a second root next to the screen's own.
                    onAllNodes(isRoot()).onFirst().assertIsDisplayed()
                }
            } catch (error: Throwable) {
                throw AssertionError("$name failed to render (${variant.label})", error)
            }
        }
}

private data class Variant(val label: String, val darkTheme: Boolean, val fontScale: Float)
