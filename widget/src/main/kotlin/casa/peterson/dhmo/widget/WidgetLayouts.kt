@file:Suppress("MagicNumber")

package casa.peterson.dhmo.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextAlign

/*
 * The six layouts. Every one is the same three things from the Today screen, scaled to the space: the jar (alone in the
 * smallest layouts, in its window from the square up), the total written as `1,250 / 2,500 ml`, and coasters. The wide and
 * tall versions of a size show the same things, arranged across or down. Sizes come from LocalSize, which is the
 * breakpoint the launcher matched; the real widget may be a little larger, and the drawings take up the slack.
 */

/** 2x1: the total and one coaster, with the jar when the cell is wide enough (landscape). */
@Composable
internal fun CompactLayout(model: WidgetModel, art: WidgetArt) {
    val size = LocalSize.current
    Row(GlanceModifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (size.width >= 190.dp) {
            JarArt(art, model, GlanceModifier.width(36.dp).fillMaxHeight())
            Spacer(GlanceModifier.width(8.dp))
        }
        Column(GlanceModifier.defaultWeight().padding(start = 4.dp)) {
            Text(model.amountNumber, style = numberStyle(17.sp), maxLines = 1)
            val undo = model.undo
            when {
                undo != null -> UndoWord(undo, 12.sp)
                model.goalLine != null -> Text(model.goalLine, style = goalStyle(10.sp, mono = false), maxLines = 1)
            }
        }
        Spacer(GlanceModifier.width(4.dp))
        if (size.height >= 60.dp) {
            Coaster(model.primaryButton, CoasterStyle.MINI, model, GlanceModifier.size(48.dp, 60.dp))
        } else {
            Coaster(model.primaryButton, CoasterStyle.INLINE, model, GlanceModifier.height(40.dp))
        }
    }
}

/**
 * 3x1: the jar, the total with the pace under it, and one coaster. Landscape cells are short (the 300 x 40 breakpoint),
 * so there it folds to one line.
 */
@Composable
internal fun WideStripLayout(model: WidgetModel, art: WidgetArt) {
    val tall = LocalSize.current.height >= 60.dp
    Row(GlanceModifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        JarArt(art, model, GlanceModifier.width(if (tall) 44.dp else 32.dp).fillMaxHeight())
        Spacer(GlanceModifier.width(10.dp))
        val undo = model.undo
        Column(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            when {
                tall && undo != null -> {
                    // "Added 250 ml" does not fit beside the jar and coaster; the total has already gone up.
                    Text(model.amountNumber, style = numberStyle(20.sp), maxLines = 1)
                    model.goalLine?.let { Text(it, style = goalStyle(12.sp, mono = false), maxLines = 1) }
                    UndoWord(undo, 12.sp)
                }

                tall -> {
                    Text(model.amountNumber, style = numberStyle(20.sp), maxLines = 1)
                    model.goalLine?.let { Text(it, style = goalStyle(12.sp, mono = false), maxLines = 1) }
                    StatusOrUndo(model, 11.sp, short = true, modifier = GlanceModifier.padding(top = 2.dp))
                }

                else -> {
                    Total(model, 18.sp, 12.sp, stacked = false)
                    StatusOrUndo(model, 11.sp, short = true)
                }
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        if (tall) {
            Coaster(model.primaryButton, CoasterStyle.MINI, model, GlanceModifier.size(56.dp, 68.dp))
        } else {
            Coaster(model.primaryButton, CoasterStyle.INLINE, model, GlanceModifier.height(40.dp))
        }
    }
}

/**
 * 1x3: the wide strip turned on end. The jar over the total, with the coaster at the bottom. The jar rather than the
 * window, as in the wide strip: one cell is too narrow for the window to be read.
 */
@Composable
internal fun TallStripLayout(model: WidgetModel, art: WidgetArt) {
    Column(GlanceModifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val portrait = LocalSize.current.height >= 280.dp
        if (portrait) {
            // A tall cell: the jar and total sit together in the middle of the space above the coaster.
            Spacer(GlanceModifier.defaultWeight())
            JarArt(art, model, GlanceModifier.fillMaxWidth().height(96.dp))
        } else {
            // A short (landscape) cell: the jar takes whatever height the rest leaves.
            JarArt(art, model, GlanceModifier.fillMaxWidth().defaultWeight())
        }
        Spacer(GlanceModifier.height(6.dp))
        Text(model.amountValue, style = numberStyle(15.sp, TextAlign.Center), maxLines = 1)
        Text(model.goalLine ?: model.unitSymbol, style = goalStyle(11.sp, TextAlign.Center, mono = false), maxLines = 2)
        val undo = model.undo
        if (undo != null) {
            UndoWord(undo, 12.sp, TextAlign.Center)
        } else {
            StatusOrUndo(model, 11.sp, short = true, align = TextAlign.Center, modifier = GlanceModifier.padding(top = 4.dp))
        }
        Spacer(if (portrait) GlanceModifier.defaultWeight() else GlanceModifier.height(8.dp))
        Coaster(model.primaryButton, CoasterStyle.MINI, model, GlanceModifier.fillMaxWidth().height(56.dp))
    }
}

/** 2x2: the window, the total and pace, and two coasters. Arranged across when the cell is wide (landscape). */
@Composable
internal fun SquareLayout(model: WidgetModel, art: WidgetArt) {
    val size = LocalSize.current
    if (size.isWide()) {
        val inner = size.height - 20.dp
        Row(GlanceModifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            WindowArt(art, model, GlanceModifier.size(inner * WidgetArt.WindowAspect, inner))
            Spacer(GlanceModifier.width(10.dp))
            Column(GlanceModifier.defaultWeight()) {
                Total(model, 20.sp, 11.sp, stacked = true)
                StatusOrUndo(model, 11.sp, short = true, modifier = GlanceModifier.padding(top = 2.dp))
            }
            Spacer(GlanceModifier.width(8.dp))
            Coaster(model.primaryButton, CoasterStyle.MINI, model, GlanceModifier.size(56.dp, min(inner, 64.dp)))
        }
        return
    }
    Column(GlanceModifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        WindowArt(art, model, GlanceModifier.fillMaxWidth().defaultWeight())
        Spacer(GlanceModifier.height(6.dp))
        Total(model, 22.sp, 11.sp, stacked = true, align = TextAlign.Center)
        StatusOrUndo(model, 11.sp, short = true, align = TextAlign.Center, modifier = GlanceModifier.padding(top = 2.dp))
        Spacer(GlanceModifier.height(8.dp))
        CoasterRow(model, maxCount = 2, style = CoasterStyle.MINI, height = 52.dp)
    }
}

/**
 * 4x2: the window beside the day's heading, total and pace, over a row of up to three coasters. A landscape cell is too
 * short for two rows, so everything goes in one line there.
 */
@Composable
internal fun WideCardLayout(model: WidgetModel, art: WidgetArt) {
    val size = LocalSize.current
    if (size.height < 150.dp) {
        ShortWideCard(model, art, size)
        return
    }
    // Sized for the breakpoint, with room to grow: the real cell is usually a little taller, and the window fills it.
    val windowHeight = size.height - 24.dp - CardCoasterHeight - 10.dp
    Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
        Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
            WindowArt(art, model, GlanceModifier.width(windowHeight * WidgetArt.WindowAspect * 1.2f).fillMaxHeight())
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight()) {
                CardHeading(model)
                Spacer(GlanceModifier.height(4.dp))
                Total(model, 26.sp, 12.sp, stacked = true)
                StatusOrUndo(model, 12.sp, modifier = GlanceModifier.padding(top = 2.dp))
            }
        }
        Spacer(GlanceModifier.height(10.dp))
        CoasterRow(model, maxCount = WidgetModel.MAX_BUTTONS, style = CoasterStyle.STACKED, height = CardCoasterHeight)
    }
}

@Composable
private fun ShortWideCard(model: WidgetModel, art: WidgetArt, size: DpSize) {
    val inner = size.height - 20.dp
    Row(GlanceModifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        WindowArt(art, model, GlanceModifier.size(inner * WidgetArt.WindowAspect, inner))
        Spacer(GlanceModifier.width(12.dp))
        Column(GlanceModifier.defaultWeight()) {
            Total(model, 20.sp, 11.sp, stacked = true)
            StatusOrUndo(model, 11.sp, short = true, modifier = GlanceModifier.padding(top = 2.dp))
        }
        Spacer(GlanceModifier.width(8.dp))
        Row(GlanceModifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            model.buttons.forEachIndexed { index, button ->
                if (index > 0) Spacer(GlanceModifier.width(6.dp))
                Coaster(button, CoasterStyle.MINI, model, GlanceModifier.size(60.dp, min(inner, 68.dp)))
            }
        }
    }
}

/**
 * 2x4: the wide card turned on end. The heading on top, the window with the total and pace under it in the middle, and the
 * coasters stacked at the bottom. A landscape 2x4 is wider than tall, so it uses the wide card.
 */
@Composable
internal fun TallCardLayout(model: WidgetModel, art: WidgetArt) {
    val size = LocalSize.current
    if (size.width > size.height * 0.8f) {
        WideCardLayout(model, art)
        return
    }
    Column(GlanceModifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(GlanceModifier.fillMaxWidth()) { CardHeading(model, lastDrinkLines = 2) }
        Spacer(GlanceModifier.height(8.dp))
        // The window takes whatever height the rest leaves, up to the full width.
        WindowArt(art, model, GlanceModifier.fillMaxWidth().defaultWeight())
        Spacer(GlanceModifier.height(8.dp))
        Total(model, 26.sp, 12.sp, stacked = true, align = TextAlign.Center)
        StatusOrUndo(model, 12.sp, stackedUndo = true, align = TextAlign.Center, modifier = GlanceModifier.padding(top = 4.dp))
        Spacer(GlanceModifier.height(10.dp))
        // Their own column: a Glance column holds at most ten children.
        Column(GlanceModifier.fillMaxWidth()) {
            model.buttons.forEachIndexed { index, button ->
                if (index > 0) Spacer(GlanceModifier.height(6.dp))
                Coaster(button, CoasterStyle.ROW, model, GlanceModifier.fillMaxWidth().height(44.dp))
            }
        }
    }
}

private val CardCoasterHeight = 64.dp

/** "Today" in the heading serif, with the time of the last drink under it ([lastDrinkLines] lets a narrow card wrap it). */
@Composable
private fun CardHeading(model: WidgetModel, lastDrinkLines: Int = 1) {
    Text(model.titleText, style = headingStyle(18.sp), maxLines = 1)
    model.lastDrinkText?.let { Text(it, style = mutedStyle(11.sp), maxLines = lastDrinkLines) }
}

/** Up to [maxCount] coasters sharing a row equally. */
@Composable
private fun CoasterRow(model: WidgetModel, maxCount: Int, style: CoasterStyle, height: Dp) {
    Row(GlanceModifier.fillMaxWidth().height(height)) {
        model.buttons.take(maxCount).forEachIndexed { index, button ->
            if (index > 0) Spacer(GlanceModifier.width(8.dp))
            Coaster(button, style, model, GlanceModifier.defaultWeight().fillMaxHeight())
        }
    }
}

private fun DpSize.isWide(): Boolean = width > height * 1.4f
