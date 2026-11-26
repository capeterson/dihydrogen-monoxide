package casa.peterson.dhmo.widget

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import casa.peterson.dhmo.core.designsystem.component.WindowStill
import casa.peterson.dhmo.core.designsystem.theme.DarkColors
import casa.peterson.dhmo.core.designsystem.theme.LightColors
import kotlin.math.roundToInt

/**
 * The two drawings the layouts show: the Today screen's arched window, and its jar on a piece of the sill for the small
 * layouts. Rendered once per update and shared by every layout, because a widget's bitmaps count against a per-update
 * memory limit and the same bitmap object is only sent once.
 */
internal class WidgetArt(val window: Bitmap, val jar: Bitmap) {
    companion object {
        /** Tallest the window is drawn, in dp: the tall card on a large phone. */
        private const val WINDOW_HEIGHT_DP = 190f

        /** Tallest the jar is drawn, in dp: the wide strip. */
        private const val JAR_HEIGHT_DP = 96f

        /** Caps the pixels on very dense screens; the window is then about 0.9 MB and the jar 0.2 MB. */
        private const val MAX_DENSITY = 3f

        val WindowAspect: Float = WindowStill.Framing.WINDOW.aspectRatio
        val JarAspect: Float = WindowStill.Framing.JAR.aspectRatio

        fun render(model: WidgetModel, density: Float): WidgetArt {
            val scale = density.coerceIn(1f, MAX_DENSITY)
            val scheme = if (model.night) DarkColors else LightColors

            fun draw(framing: WindowStill.Framing, heightDp: Float): Bitmap = WindowStill.render(
                framing = framing,
                heightPx = (heightDp * scale).roundToInt(),
                scheme = scheme,
                night = model.night,
                progress = model.fraction,
                raining = model.raining,
            ).asAndroidBitmap()

            return WidgetArt(
                window = draw(WindowStill.Framing.WINDOW, WINDOW_HEIGHT_DP),
                jar = draw(WindowStill.Framing.JAR, JAR_HEIGHT_DP),
            )
        }
    }
}
