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

/*
 * Pieces shared by every layout. Widgets cannot use the app's bundled fonts, so headings use the system serif and
 * quantities the system monospace, the closest stand-ins for Fraunces and DM Mono.
 */

/** The paper the widget sits on: the same `surfaceContainerLow` as the app's cards, in both themes. */
private val Paper = ColorProvider(day = LightColors.surfaceContainerLow, night = DarkColors.surfaceContainerLow)

/** A tap target is never smaller than this, whatever the pill drawn inside it looks like. */
internal val MinTouchTarget = 48.dp

/** What tapping the widget outside a button does. `:widget` cannot see `:app`, so the launcher activity is named. */
private const val MAIN_ACTIVITY = "casa.peterson.dhmo.MainActivity"

/** Teal while the day is in progress, amber (the lamp's colour) once the goal is reached. */
@Composable
internal fun WidgetModel.progressColor(): ColorProvider = if (goalReached) GlanceTheme.colors.secondary else GlanceTheme.colors.primary

@Composable
private fun openAppAction(): Action = actionStartActivity(ComponentName(LocalContext.current.packageName, MAIN_ACTIVITY))

/** The widget's paper, rounded to match the launcher on Android 12 and later. Tapping anywhere outside a button opens the app. */
@Composable
internal fun WidgetFrame(padding: PaddingValues, content: @Composable () -> Unit) {
    var modifier = GlanceModifier.fillMaxSize().appWidgetBackground().background(Paper)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        modifier = modifier.cornerRadius(android.R.dimen.system_app_widget_background_radius)
    }
    Box(
        modifier = modifier.padding(start = padding.start, top = padding.top, end = padding.end, bottom = padding.bottom)
            .clickable(openAppAction()),
        contentAlignment = Alignment.Center,
    ) { content() }
}

internal data class PaddingValues(val start: Dp, val top: Dp, val end: Dp, val bottom: Dp) {
    constructor(horizontal: Dp, vertical: Dp) : this(horizontal, vertical, horizontal, vertical)
    constructor(all: Dp) : this(all, all, all, all)
}

@Composable
internal fun WidgetText(
    text: String,
    size: TextUnit,
    modifier: GlanceModifier = GlanceModifier,
    color: ColorProvider = GlanceTheme.colors.onSurface,
    weight: FontWeight = FontWeight.Normal,
    family: FontFamily? = null,
    align: TextAlign? = null,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = size, fontWeight = weight, fontFamily = family, textAlign = align),
        maxLines = maxLines,
    )
}

/** A quantity such as `1.25 L`: monospace, bold. */
@Composable
internal fun AmountText(text: String, size: TextUnit, modifier: GlanceModifier = GlanceModifier, align: TextAlign? = null) =
    WidgetText(text, size, modifier, weight = FontWeight.Bold, family = FontFamily.Monospace, align = align)

/** A small line of secondary text. */
@Composable
internal fun CaptionText(text: String, modifier: GlanceModifier = GlanceModifier, size: TextUnit = 12.sp, align: TextAlign? = null) =
    WidgetText(text, size, modifier, color = GlanceTheme.colors.onSurfaceVariant, align = align)

/** "Today", in the heading serif. */
@Composable
internal fun HeadingText(text: String, modifier: GlanceModifier = GlanceModifier, size: TextUnit = 14.sp) =
    WidgetText(text, size, modifier, weight = FontWeight.Bold, family = FontFamily.Serif)

@Composable
internal fun DropIcon(size: Dp, modifier: GlanceModifier = GlanceModifier) {
    Image(
        provider = ImageProvider(DhmoIcons.WaterDrop),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
    )
}

/** A monospace glyph is about this many ems wide. */
private const val MONO_EM = 0.62f

/** Whether [text] fits one line of [available] width at [size]. */
internal fun fits(text: String, available: Dp, size: TextUnit): Boolean = text.length * MONO_EM * size.value <= available.value

/** The largest size up to [max] at which [text] still fits [available] on one line; widgets cannot measure, so this estimates. */
internal fun fittedSize(text: String, available: Dp, max: TextUnit, min: TextUnit = 9.sp): TextUnit =
    (available.value / (text.length.coerceAtLeast(1) * MONO_EM)).coerceIn(min.value, max.value).sp
