package casa.peterson.dhmo.core.designsystem.component

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import casa.peterson.dhmo.core.designsystem.theme.DarkColors
import casa.peterson.dhmo.core.designsystem.theme.LightColors
import com.google.common.truth.Truth.assertThat
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The widget's still frames: sized as asked, and drawing the jar's actual level. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WindowStillTest {
    private fun jar(progress: Float, night: Boolean = false): Bitmap = WindowStill.render(
        WindowStill.Framing.JAR,
        300,
        if (night) DarkColors else LightColors,
        night,
        progress,
        raining = false,
    ).asAndroidBitmap()

    /** A point inside the jar's lower half, where water shows once the jar is a quarter full. */
    private fun Bitmap.lowerJar(): Int = getPixel(width / 2, (height * 0.72f).roundToInt())

    private fun Int.isBluish(): Boolean = Color.blue(this) > Color.red(this) + 20

    @Test
    fun `each framing keeps its proportions at the requested height`() {
        WindowStill.Framing.entries.forEach { framing ->
            val image = WindowStill.render(framing, 400, LightColors, night = false, progress = 0.5f, raining = true)
            assertThat(image.height).isEqualTo(400)
            assertThat(image.width).isEqualTo((400 * framing.aspectRatio).roundToInt())
        }
    }

    @Test
    fun `an empty jar is clear and a filled one holds water`() {
        assertThat(jar(0f).lowerJar().isBluish()).isFalse()
        assertThat(jar(0.6f).lowerJar().isBluish()).isTrue()
        assertThat(jar(1f, night = true).lowerJar().isBluish()).isTrue()
    }

    @Test
    fun `day and night draw differently`() {
        val window = { night: Boolean ->
            WindowStill.render(
                WindowStill.Framing.WINDOW,
                200,
                if (night) DarkColors else LightColors,
                night,
                0.5f,
                false,
            ).asAndroidBitmap()
        }
        val day = window(false)
        val night = window(true)
        // The sky at the top of the arch.
        assertThat(day.getPixel(day.width / 2, day.height / 6)).isNotEqualTo(night.getPixel(night.width / 2, night.height / 6))
    }
}
