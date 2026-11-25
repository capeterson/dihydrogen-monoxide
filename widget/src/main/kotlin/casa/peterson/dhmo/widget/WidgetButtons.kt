package casa.peterson.dhmo.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
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

/** The deep link the app's Today screen answers. */
private const val TODAY_LINK = "dhmo://today"
private const val PILL_RADIUS_FACTOR = 2

private fun todayLinkIntent(context: Context): Intent = Intent(Intent.ACTION_VIEW, TODAY_LINK.toUri()).setPackage(context.packageName)

private fun buttonAction(button: WidgetButton, context: Context): Action = if (button.containerId == null) {
    actionStartIntent(todayLinkIntent(context))
} else {
    actionRunCallback<LogContainerAction>(actionParametersOf(ContainerIdKey to button.containerId.toString()))
}

/**
 * One add button: a teal pill inside a transparent tap target of at least [MinTouchTarget]. Tapping logs the container
 * (or, with no container yet, opens Today). [showIcon] puts the container's drawing before the label, [short] uses
 * the label without its unit.
 */
@Composable
internal fun AddButton(
    button: WidgetButton,
    modifier: GlanceModifier = GlanceModifier,
    showIcon: Boolean = false,
    short: Boolean = false,
    textSize: TextUnit = 13.sp,
    horizontalPadding: Dp = 10.dp,
    fillWidth: Boolean = false,
) {
    val context = LocalContext.current
    Box(
        modifier = modifier.height(MinTouchTarget).clickable(buttonAction(button, context))
            .semantics { contentDescription = button.contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = (if (fillWidth) GlanceModifier.fillMaxWidth() else GlanceModifier).height(
                PillHeight,
            ).cornerRadius(PillHeight / PILL_RADIUS_FACTOR)
                .background(GlanceTheme.colors.primary).padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showIcon && button.icon != null) {
                Image(
                    provider = ImageProvider(button.icon.drawableRes()),
                    contentDescription = null,
                    modifier = GlanceModifier.size(ButtonIconSize),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
                )
                Spacer(GlanceModifier.width(ButtonIconGap))
            }
            WidgetText(
                text = if (short) button.shortLabel else button.label,
                size = textSize,
                color = GlanceTheme.colors.onPrimary,
                weight = FontWeight.Bold,
                family = FontFamily.Monospace,
            )
        }
    }
}

private val PillHeight = 36.dp
internal val ButtonIconSize = 14.dp
internal val ButtonIconGap = 3.dp

/** The "Finish setup in the app" state, the same in every layout; only the room it has differs. */
@Composable
internal fun SetupContent(model: WidgetModel, narrow: Boolean) {
    if (narrow) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = GlanceModifier.fillMaxSize().semantics {
                contentDescription = model.setupDescription
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DropIcon(20.dp)
            Spacer(GlanceModifier.height(6.dp))
            WidgetText(model.setupText, 11.sp, align = TextAlign.Center, maxLines = 5)
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.fillMaxSize().semantics { contentDescription = model.setupDescription },
        ) {
            DropIcon(24.dp)
            Spacer(GlanceModifier.width(8.dp))
            WidgetText(model.setupText, 14.sp, family = FontFamily.Serif, weight = FontWeight.Bold, maxLines = 2)
        }
    }
}
