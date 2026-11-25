package casa.peterson.dhmo.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width

/** Shorter than this (a landscape 2x4 is about 250 dp), three stacked buttons do not fit: the wide card's arrangement is used. */
private val StackedMinHeight = 380.dp

private val CardPadding = 12.dp

/**
 * 2x4: the wide card stood on end. Header, last drink, amount, the goal, percentage and pace on separate lines, a water
 * level that takes the spare height, and up to three add buttons at full width.
 */
@Composable
internal fun TallCardLayout(model: WidgetModel) {
    val size = LocalSize.current
    if (size.height < StackedMinHeight) {
        WideCardLayout(model)
        return
    }
    WidgetFrame(PaddingValues(CardPadding)) {
        if (model.needsSetup) {
            SetupContent(model, narrow = true)
        } else {
            val inner = size.width - CardPadding * 2
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DropIcon(16.dp)
                    Spacer(GlanceModifier.width(4.dp))
                    HeadingText(model.titleText)
                }
                model.lastDrinkText?.let { CaptionText(it, size = 11.sp) }
                Column(modifier = GlanceModifier.fillMaxWidth().progressSemantics(model)) {
                    Spacer(GlanceModifier.height(6.dp))
                    AmountText(model.amountText, fittedSize(model.amountText, inner, 26.sp))
                    model.goalLine?.let { CaptionText(it, size = 13.sp) }
                    model.percentText?.let { CaptionText(it, size = 13.sp) }
                    model.paceText?.let { CaptionText(it, size = 13.sp) }
                }
                Spacer(GlanceModifier.height(8.dp))
                if (model.goalText != null) {
                    LevelFill(model, GlanceModifier.fillMaxWidth().defaultWeight())
                } else {
                    Spacer(GlanceModifier.defaultWeight())
                }
                // One child, not three: a RemoteViews container holds at most ten children, and the rest are silently dropped.
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    val available = inner - 20.dp - ButtonIconSize - ButtonIconGap - 4.dp
                    val short = model.buttons.any { !fits(it.label, available, MIN_LABEL_SIZE) }
                    model.buttons.forEach { button ->
                        Spacer(GlanceModifier.height(2.dp))
                        AddButton(
                            button,
                            GlanceModifier.fillMaxWidth(),
                            showIcon = true,
                            short = short,
                            textSize = fittedSize(if (short) button.shortLabel else button.label, available, 13.sp),
                            fillWidth = true,
                        )
                    }
                }
            }
        }
    }
}
