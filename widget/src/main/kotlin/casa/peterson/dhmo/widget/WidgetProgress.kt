package casa.peterson.dhmo.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity as actionStartIntent
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import casa.peterson.dhmo.core.designsystem.icon.DhmoIcons
import casa.peterson.dhmo.core.designsystem.icon.drawableRes
import casa.peterson.dhmo.core.designsystem.theme.DarkColors
import casa.peterson.dhmo.core.designsystem.theme.LightColors

/** The horizontal progress bar: teal, or amber at goal, on the variant surface. Absent while there is no goal. */
@Composable
internal fun ProgressBar(model: WidgetModel, modifier: GlanceModifier = GlanceModifier.fillMaxWidth()) {
    if (model.goalText == null) return
    LinearProgressIndicator(
        progress = model.fraction,
        modifier = modifier,
        color = model.progressColor(),
        backgroundColor = GlanceTheme.colors.surfaceVariant,
    )
}

/**
 * A vertical water level. Glance has no vertical progress indicator, and a fill sized in dp would be wrong in every widget
 * larger than its breakpoint, so the level is a small bitmap (track colour with the water painted from the bottom) that
 * the launcher stretches to whatever height this slot is given. Absent while there is no goal.
 */
@Composable
internal fun LevelFill(model: WidgetModel, modifier: GlanceModifier = GlanceModifier) {
    if (model.goalText == null) return
    val context = LocalContext.current
    val water = model.progressColor().getColor(context).toArgb()
    val track = GlanceTheme.colors.surfaceVariant.getColor(context).toArgb()
    // The image sits in a box that takes the weight, so that its own size never competes with the siblings for space.
    Box(modifier = modifier) {
        Image(
            provider = ImageProvider(levelBitmap(model.fraction, water, track)),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize().cornerRadius(LEVEL_RADIUS),
            contentScale = ContentScale.FillBounds,
        )
    }
}

private val LEVEL_RADIUS = 8.dp
private const val LEVEL_BITMAP_WIDTH = 24
private const val LEVEL_BITMAP_HEIGHT = 200

private fun levelBitmap(fraction: Float, water: Int, track: Int): Bitmap {
    val bitmap = createBitmap(LEVEL_BITMAP_WIDTH, LEVEL_BITMAP_HEIGHT)
    val canvas = Canvas(bitmap)
    canvas.drawColor(track)
    val level = fraction.coerceIn(0f, 1f)
    if (level > 0f) {
        // A drink always shows: at least a few pixels of water.
        val height = (level * LEVEL_BITMAP_HEIGHT).coerceAtLeast(MIN_WATER_PX)
        canvas.drawRect(
            0f,
            LEVEL_BITMAP_HEIGHT - height,
            LEVEL_BITMAP_WIDTH.toFloat(),
            LEVEL_BITMAP_HEIGHT.toFloat(),
            Paint().apply { color = water },
        )
    }
    return bitmap
}

private const val MIN_WATER_PX = 6f

/** Describes the progress area to TalkBack as one sentence that includes the percentage. */
internal fun GlanceModifier.progressSemantics(model: WidgetModel): GlanceModifier =
    semantics { contentDescription = model.progressDescription }
