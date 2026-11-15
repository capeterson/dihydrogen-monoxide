package casa.peterson.dhmo.core.designsystem.screenshot

import androidx.compose.runtime.Composable
import casa.peterson.dhmo.core.testing.captureScreenVariants
import java.io.File
import javax.imageio.ImageIO

private const val MAX_ATTEMPTS = 4
private const val ALPHA_SHIFT = 24

/**
 * [captureScreenVariants], retried when Roborazzi 1.79 on Robolectric NATIVE graphics intermittently hands back a
 * blank (fully transparent) frame for the second or third variant of a capture. A real rendering change still fails:
 * verify throws on every attempt, and a record run never writes a blank reference.
 */
internal fun captureStable(name: String, content: @Composable () -> Unit) {
    var failure: Throwable? = null
    repeat(MAX_ATTEMPTS) {
        failure =
            try {
                captureScreenVariants(name, content)
                if (writtenImagesAreBlank(name)) AssertionError("A blank frame was written for $name") else return
            } catch (error: AssertionError) {
                error
            }
    }
    throw checkNotNull(failure)
}

/** True when a recorded `<name>_*.png` has a transparent top-left pixel (every real screen has an opaque background). */
private fun writtenImagesAreBlank(name: String): Boolean {
    val directory = File(System.getProperty("roborazzi.output.dir") ?: "src/test/screenshots")
    return listOf("light", "dark", "largefont").any { suffix ->
        val file = File(directory, "${name}_$suffix.png")
        file.exists() && (ImageIO.read(file).getRGB(0, 0) ushr ALPHA_SHIFT) == 0
    }
}
