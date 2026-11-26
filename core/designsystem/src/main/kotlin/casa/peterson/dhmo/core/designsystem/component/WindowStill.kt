package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import casa.peterson.dhmo.core.designsystem.theme.DarkExtraColors
import casa.peterson.dhmo.core.designsystem.theme.LightExtraColors
import kotlin.math.roundToInt

private const val DESIGN_HEIGHT = DESIGN_WIDTH * HEIGHT_PER_WIDTH

/** The sill overhangs the window by 8 design units each side, and its shadow falls 5 below it. */
private const val SILL_OVERHANG = 10f
private const val SILL_SHADOW = 6f

/** The goal glow behind a cropped jar ends at the sill's top (287.5 - 16). */
private const val JAR_GLOW_BOTTOM = 271.5f

/**
 * Still frames of [WindowHero] for surfaces that cannot run Compose UI, such as the home-screen widget. They are drawn by
 * the hero's own code with motion at rest, so the jar, lantern, rain and water spirit match the Today screen exactly: the
 * jar holds [progress] of the goal, the lantern is lit once the goal is met, and it rains while the day is behind pace.
 */
object WindowStill {
    /** Which part of the scene to draw, as a rectangle in the hero's 230 x 287.5 design units. */
    enum class Framing(internal val bounds: Rect, internal val window: Boolean) {
        /** The whole arched window on its sill, with the plant, jar, water spirit and lantern. */
        WINDOW(Rect(-SILL_OVERHANG, 0f, DESIGN_WIDTH + SILL_OVERHANG, DESIGN_HEIGHT + SILL_SHADOW), window = true),

        /** Only the jar, standing on a piece of the sill. For spaces too small for the window. */
        JAR(Rect(72f, 188f, 132f, DESIGN_HEIGHT + SILL_SHADOW), window = false),

        /** The jar and the water spirit beside it, on a piece of the sill. */
        JAR_AND_SPIRIT(Rect(66f, 188f, 180f, DESIGN_HEIGHT + SILL_SHADOW), window = false),
        ;

        /** Width divided by height. */
        val aspectRatio: Float get() = bounds.width / bounds.height
    }

    /**
     * Draws [framing] into a new bitmap [heightPx] pixels tall (its width follows [Framing.aspectRatio]), with [scheme]'s
     * colours and the night or day sky. The background is transparent outside the drawing.
     */
    fun render(framing: Framing, heightPx: Int, scheme: ColorScheme, night: Boolean, progress: Float, raining: Boolean): ImageBitmap {
        require(heightPx > 0) { "heightPx must be positive" }
        val bounds = framing.bounds
        val scale = heightPx / bounds.height
        val image = ImageBitmap((bounds.width * scale).roundToInt().coerceAtLeast(1), heightPx)
        val canvas = Canvas(image)
        canvas.translate(-bounds.left * scale, -bounds.top * scale)
        val look = HeroLook(
            if (night) DarkExtraColors else LightExtraColors,
            scheme.primary,
            scheme.tertiary,
            scheme.onSurfaceVariant,
            scheme.surfaceVariant,
            scheme.secondary,
        )
        val fraction = jarFraction(progress)
        val heroSize = Size(DESIGN_WIDTH * scale, DESIGN_HEIGHT * scale)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, heroSize) {
            val lit = fraction >= 1f
            if (lit && !framing.window) {
                // The lantern is out of frame, so its lamplight falls behind the jar instead and the goal still reads as
                // met. An ellipse inside the frame, so the glow fades out before the edges rather than being cut by them.
                val rx = bounds.width / 2f * scale
                val ry = (JAR_GLOW_BOTTOM - bounds.top) / 2f * scale
                val center = Offset(bounds.center.x * scale, (bounds.top * scale) + ry)
                scale(rx / ry, 1f, center) {
                    drawCircle(
                        Brush.radialGradient(0f to look.x.glowHalo, 1f to Color.Transparent, center = center, radius = ry),
                        ry,
                        center,
                    )
                }
            }
            // time 0 and every event phase at 1 is the hero at rest: no pour, no hop, no fireflies.
            drawHero(
                look = look,
                raining = raining,
                lit = lit,
                level = fraction,
                time = 0f,
                sip = 1f,
                spiritSip = 1f,
                goal = 1f,
                window = framing.window,
            )
        }
        return image
    }
}
