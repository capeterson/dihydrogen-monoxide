package casa.peterson.dhmo.core.testing

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import casa.peterson.dhmo.core.designsystem.theme.DhmoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.robolectric.RuntimeEnvironment

private const val LARGE_FONT_SCALE = 2f
private const val CAPTURE_ATTEMPTS = 4

/** 411 x 891 dp at 420 dpi (density 2.625). */
private const val SCREEN_QUALIFIERS = "w411dp-h891dp-420dpi"

/**
 * Captures `<name>_light.png`, `<name>_dark.png` and `<name>_largefont.png` at 411 x 891 dp, 2.625 density.
 *
 * Call it from a Robolectric test annotated with `@GraphicsMode(GraphicsMode.Mode.NATIVE)`. Without
 * `recordRoborazziDebug`, `verifyRoborazziDebug` or `compareRoborazziDebug`, `captureRoboImage` does nothing.
 * Capture stateless `...Content(uiState, callbacks)` composables with fixed state and a fixed clock.
 */
fun captureScreenVariants(name: String, suffix: String? = null, content: @Composable () -> Unit) {
    RuntimeEnvironment.setQualifiers(SCREEN_QUALIFIERS)
    val directory = System.getProperty("roborazzi.output.dir") ?: "src/test/screenshots"
    listOf(
        Variant("light", darkTheme = false, fontScale = 1f),
        Variant("dark", darkTheme = true, fontScale = 1f),
        Variant("largefont", darkTheme = false, fontScale = LARGE_FONT_SCALE),
    ).filter { suffix == null || it.suffix == suffix }.forEach { variant ->
        var failure: AssertionError? = null
        for (attempt in 0 until CAPTURE_ATTEMPTS) {
            try {
                captureRoboImage(filePath = File(directory, "${name}_${variant.suffix}.png").path) {
                    val base = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = variant.fontScale)) {
                        DhmoTheme(darkTheme = variant.darkTheme, dynamicColor = false) {
                            Surface(modifier = Modifier.fillMaxSize()) { content() }
                        }
                    }
                }
                failure = null
                break
            } catch (error: AssertionError) {
                failure = error
            }
        }
        failure?.let { throw it }
    }
}

private data class Variant(val suffix: String, val darkTheme: Boolean, val fontScale: Float)
