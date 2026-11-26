package casa.peterson.dhmo.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
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

/** What every text size is multiplied by, so the system font scale stays within the layout's cap (see [textScaleFor]). */
internal val LocalTextScale = staticCompositionLocalOf { 1f }

/** [size] in sp, reduced as [LocalTextScale] says. */
@Composable
internal fun scaled(size: TextUnit): TextUnit = (size.value * LocalTextScale.current).sp

/** DM Mono on the Today screen; widgets can only use system fonts, so the system monospace stands in. */
private val Numerals = FontFamily.Monospace

/** Fraunces on the Today screen; the system serif stands in. */
private val Heading = FontFamily.Serif

@Composable
internal fun numberStyle(size: TextUnit, align: TextAlign = TextAlign.Start) = TextStyle(
    color = GlanceTheme.colors.onSurface,
    fontSize = scaled(size),
    fontWeight = FontWeight.Medium,
    fontFamily = Numerals,
    textAlign = align,
)

@Composable
internal fun goalStyle(size: TextUnit, align: TextAlign = TextAlign.Start, mono: Boolean = true) = TextStyle(
    color = GlanceTheme.colors.onSurfaceVariant,
    fontSize = scaled(size),
    fontFamily = if (mono) Numerals else null,
    textAlign = align,
)

@Composable
internal fun headingStyle(size: TextUnit) =
    TextStyle(color = GlanceTheme.colors.onSurface, fontSize = scaled(size), fontWeight = FontWeight.Medium, fontFamily = Heading)

@Composable
internal fun mutedStyle(size: TextUnit, align: TextAlign = TextAlign.Start) =
    TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = scaled(size), textAlign = align)

@Composable
private fun statusColor(model: WidgetModel): ColorProvider =
    if (model.statusTone == StatusTone.LAMPLIGHT) GlanceTheme.colors.secondary else GlanceTheme.colors.primary

/** The total as the Today screen writes it: a large `1,250` and a smaller `/ 2,500 ml`, side by side or [stacked]. */
@Composable
internal fun Total(
    model: WidgetModel,
    numberSize: TextUnit,
    goalSize: TextUnit,
    stacked: Boolean,
    align: TextAlign = TextAlign.Start,
    modifier: GlanceModifier = GlanceModifier,
) {
    val suffix = model.goalSuffix
    if (stacked || suffix == null) {
        Column(modifier, horizontalAlignment = align.horizontal()) {
            Text(model.amountNumber, style = numberStyle(numberSize, align), maxLines = 1)
            if (suffix != null) Text(suffix, style = goalStyle(goalSize, align), maxLines = 1)
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.Bottom) {
            Text(model.amountNumber, style = numberStyle(numberSize), maxLines = 1)
            Spacer(GlanceModifier.width(6.dp))
            Text(suffix, style = goalStyle(goalSize), maxLines = 1, modifier = GlanceModifier.padding(bottom = 2.dp))
        }
    }
}

/**
 * The status line, or, for a few seconds after a drink is logged here, "Added 250 ml · Undo". Undo is its own line rather
 * than a change to the coaster, so tapping a coaster twice to log two glasses never undoes the first.
 */
@Composable
internal fun StatusOrUndo(
    model: WidgetModel,
    size: TextUnit,
    short: Boolean = false,
    stackedUndo: Boolean = false,
    align: TextAlign = TextAlign.Start,
    modifier: GlanceModifier = GlanceModifier,
) {
    val undo = model.undo
    if (undo != null) {
        UndoLine(undo, size, align, stackedUndo, modifier)
        return
    }
    val text = (if (short) model.statusShortText else model.statusText) ?: return
    Text(
        text,
        modifier = modifier,
        style = TextStyle(color = statusColor(model), fontSize = scaled(size), fontWeight = FontWeight.Medium, textAlign = align),
        maxLines = 2,
    )
}

@Composable
private fun UndoLine(undo: WidgetUndo, size: TextUnit, align: TextAlign, stacked: Boolean, modifier: GlanceModifier) {
    val action = actionRunCallback<UndoLastDrinkAction>(actionParametersOf(EntryIdKey to undo.entryId.toString()))
    val target = modifier.clickable(action).semantics { contentDescription = undo.description }
    val word = LocalContext.current.getString(R.string.widget_undo)
    val wordStyle = TextStyle(color = GlanceTheme.colors.primary, fontSize = scaled(size), fontWeight = FontWeight.Bold, textAlign = align)
    if (stacked) {
        Column(target, horizontalAlignment = align.horizontal()) {
            Text(undo.text, style = mutedStyle(size, align), maxLines = 1)
            Text(word, style = wordStyle, maxLines = 1)
        }
    } else {
        Row(target, verticalAlignment = Alignment.CenterVertically, horizontalAlignment = align.horizontal()) {
            Text(undo.text, style = mutedStyle(size), maxLines = 1)
            Spacer(GlanceModifier.width(8.dp))
            Text(word, style = wordStyle, maxLines = 1)
        }
    }
}

/** The narrow layouts' Undo: just the word, under the total. */
@Composable
internal fun UndoWord(undo: WidgetUndo, size: TextUnit, align: TextAlign = TextAlign.Start, modifier: GlanceModifier = GlanceModifier) {
    val action = actionRunCallback<UndoLastDrinkAction>(actionParametersOf(EntryIdKey to undo.entryId.toString()))
    Text(
        LocalContext.current.getString(R.string.widget_undo),
        modifier = modifier.clickable(action).semantics { contentDescription = undo.description }.padding(vertical = 4.dp),
        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = scaled(size), fontWeight = FontWeight.Bold, textAlign = align),
        maxLines = 1,
    )
}

internal fun TextAlign.horizontal(): Alignment.Horizontal = when (this) {
    TextAlign.Center -> Alignment.CenterHorizontally
    TextAlign.End, TextAlign.Right -> Alignment.End
    else -> Alignment.Start
}
