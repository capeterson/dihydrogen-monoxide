package casa.peterson.dhmo.core.designsystem.theme

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.random.Random

private const val TILE = 128
private const val DAY_GRAIN = 0.06f
private const val NIGHT_GRAIN = 0.09f
private const val GRAY_LEVELS = 256
private const val OPAQUE = 0xFF

/**
 * The design's film grain: a fixed tile of grey noise laid over everything, multiplied in the day theme (6%) and
 * overlaid at night (9%). The noise is seeded, so a frame renders the same every time.
 */
@Composable
internal fun Modifier.paperGrain(night: Boolean): Modifier {
    val brush =
        remember {
            val pixels = IntArray(TILE * TILE)
            val random = Random(GRAIN_SEED)
            for (i in pixels.indices) {
                val v = random.nextInt(GRAY_LEVELS)
                pixels[i] = (OPAQUE shl 24) or (v shl 16) or (v shl 8) or v
            }
            val bitmap = Bitmap.createBitmap(pixels, TILE, TILE, Bitmap.Config.ARGB_8888)
            ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
        }
    return graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        drawRect(brush, alpha = if (night) NIGHT_GRAIN else DAY_GRAIN, blendMode = if (night) BlendMode.Overlay else BlendMode.Multiply)
    }
}

private const val GRAIN_SEED = 7
