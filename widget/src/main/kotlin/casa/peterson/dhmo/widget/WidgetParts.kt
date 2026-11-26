@file:Suppress("MagicNumber")

package casa.peterson.dhmo.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
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
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity as actionStartIntent
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
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

/** `:widget` cannot see `:app`, so the launcher activity is named. */
private const val MAIN_ACTIVITY = "casa.peterson.dhmo.MainActivity"
private const val TODAY_LINK = "dhmo://today"

/** Opens the app where it was. */
@Composable
internal fun openAppAction(): Action = actionStartActivity(ComponentName(LocalContext.current.packageName, MAIN_ACTIVITY))

/** Opens the Today screen, where containers are managed. */
internal fun openTodayAction(context: Context): Action =
    actionStartIntent(Intent(Intent.ACTION_VIEW, TODAY_LINK.toUri()).setPackage(context.packageName))

/**
 * The widget's sheet of paper: the app's background colour, clipped to the launcher's own corner radius from Android 12.
 * Tapping anywhere that is not a coaster or Undo opens the app.
 */
@Composable
internal fun WidgetSurface(model: WidgetModel, content: @Composable () -> Unit) {
    val paper = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceModifier.background(GlanceTheme.colors.background).cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        GlanceModifier.background(ImageProvider(if (model.night) R.drawable.widget_paper_night else R.drawable.widget_paper_day))
    }
    Box(GlanceModifier.fillMaxSize().appWidgetBackground().then(paper).clickable(openAppAction())) { content() }
}

/** The Today screen's arched window, filling [modifier]'s space without distortion. */
@Composable
internal fun WindowArt(art: WidgetArt, model: WidgetModel, modifier: GlanceModifier) {
    Image(ImageProvider(art.window), contentDescription = model.progressDescription, modifier = modifier, contentScale = ContentScale.Fit)
}

/** The jar on its piece of sill, filling [modifier]'s space without distortion. */
@Composable
internal fun JarArt(art: WidgetArt, model: WidgetModel, modifier: GlanceModifier) {
    Image(ImageProvider(art.jar), contentDescription = model.progressDescription, modifier = modifier, contentScale = ContentScale.Fit)
}

/** How a coaster arranges its icon, name and amount. */
internal enum class CoasterStyle {
    /** Icon over name over amount, as on the Today screen. */
    STACKED,

    /** Icon, name and amount in a line, for coasters stacked in a column. */
    ROW,

    /** Icon over amount, for the narrow layouts. */
    MINI,

    /** Icon beside amount, for cells too short for MINI (a landscape row). */
    INLINE,
}

/**
 * A coaster from the Today screen: the dashed-edged paper disc with the container's inked icon. Tapping it logs the
 * container; with no containers it reads "Add" and opens the app instead.
 */
@Composable
internal fun Coaster(button: WidgetButton, style: CoasterStyle, model: WidgetModel, modifier: GlanceModifier) {
    val context = LocalContext.current
    val action = button.containerId?.let {
        actionRunCallback<LogContainerAction>(actionParametersOf(ContainerIdKey to it.toString()))
    } ?: openTodayAction(context)
    val face = if (model.night) R.drawable.widget_coaster_night else R.drawable.widget_coaster_day
    val icon = button.icon?.drawableRes() ?: DhmoIcons.Add
    Box(
        modifier
            .background(ImageProvider(face))
            .clickable(action, rippleOverride = R.drawable.widget_coaster_ripple)
            .semantics { contentDescription = button.contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        // Long amounts ("11.8 fl oz") switch from the monospace to the narrower body face so they still fit.
        val amount = button.amountText
        val mono = amount != null && amount.length <= MONO_AMOUNT_LENGTH
        when (style) {
            CoasterStyle.STACKED -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = GlanceModifier.padding(4.dp)) {
                CoasterIcon(icon, 20.dp)
                Text(
                    button.name,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = scaled(12.sp), textAlign = TextAlign.Center),
                    maxLines = 1,
                )
                amount?.let { Text(it, style = goalStyle(11.sp, TextAlign.Center, mono), maxLines = 1) }
            }

            CoasterStyle.ROW -> Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.padding(horizontal = 8.dp)) {
                CoasterIcon(icon, 18.dp)
                Spacer(GlanceModifier.width(8.dp))
                Column {
                    Text(button.name, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = scaled(12.sp)), maxLines = 1)
                    amount?.let { Text(it, style = goalStyle(11.sp, mono = mono), maxLines = 1) }
                }
            }

            CoasterStyle.INLINE -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = GlanceModifier.padding(horizontal = 8.dp),
            ) {
                CoasterIcon(icon, 16.dp)
                Spacer(GlanceModifier.width(4.dp))
                Text(amount ?: button.name, style = goalStyle(11.sp, mono = mono), maxLines = 1)
            }

            CoasterStyle.MINI -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = GlanceModifier.padding(2.dp)) {
                CoasterIcon(icon, 18.dp)
                if (amount == null || mono) {
                    Text(amount ?: button.name, style = goalStyle(11.sp, TextAlign.Center, mono = amount != null), maxLines = 1)
                } else {
                    // Too long for one line in a narrow coaster: the number, then the unit under it.
                    Text(button.amountValue.orEmpty(), style = goalStyle(11.sp, TextAlign.Center), maxLines = 1)
                    Text(button.unitSymbol.orEmpty(), style = goalStyle(10.sp, TextAlign.Center, mono = false), maxLines = 1)
                }
            }
        }
    }
}

/** Amounts up to `250 ml` or `0.25 L` fit a coaster in the monospace. */
private const val MONO_AMOUNT_LENGTH = 7

@Composable
private fun CoasterIcon(icon: Int, size: Dp) {
    Image(
        ImageProvider(icon),
        contentDescription = null,
        modifier = GlanceModifier.size(size),
        colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
    )
}

/** How much room the setup invitation has. */
internal enum class SetupSpace { NARROW, SHORT, ROOMY }

/** Before onboarding is done there is nothing to show yet: a kind invitation to open the app, in every layout. */
@Composable
internal fun SetupContent(model: WidgetModel, space: SetupSpace) {
    Column(
        GlanceModifier.fillMaxSize().padding(horizontal = if (space == SetupSpace.NARROW) 4.dp else 14.dp, vertical = 10.dp)
            .semantics { contentDescription = model.setupDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val titleSize = when (space) {
            SetupSpace.NARROW -> 14.sp
            SetupSpace.SHORT -> 16.sp
            SetupSpace.ROOMY -> 18.sp
        }
        Text(model.setupTitle, style = headingStyle(titleSize).copy(textAlign = TextAlign.Center), maxLines = 4)
        if (space == SetupSpace.ROOMY) {
            Spacer(GlanceModifier.size(6.dp))
            Text(model.setupBody, style = mutedStyle(12.sp, TextAlign.Center), maxLines = 4)
        }
    }
}
