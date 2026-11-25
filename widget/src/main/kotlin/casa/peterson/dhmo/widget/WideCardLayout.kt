package casa.peterson.dhmo.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
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
import androidx.glance.text.TextAlign

/** Shorter than this (a 4x2 in landscape), the card puts its buttons beside the text instead of under it. */
private val RoomyMinHeight = 150.dp

private val CardPadding = 14.dp
private val ButtonGap = 4.dp
private val ButtonPadding = 4.dp
private val ButtonSlack = 2.dp

/**
 * 4x2: "Today" and the last drink on top, the amount large, "of 2.5 L - 50% - 250 ml behind", a progress bar, and a row
 * of up to three add buttons, each with the container's drawing.
 */
@Composable
internal fun WideCardLayout(model: WidgetModel) {
    val size = LocalSize.current
    WidgetFrame(PaddingValues(CardPadding)) {
        when {
            model.needsSetup -> SetupContent(model, narrow = false)
            size.height >= RoomyMinHeight -> RoomyWideCard(model, size.width - CardPadding * 2)
            else -> DenseWideCard(model, size.width - CardPadding * 2)
        }
    }
}

@Composable
private fun CardHeader(model: WidgetModel) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DropIcon(16.dp)
        Spacer(GlanceModifier.width(4.dp))
        HeadingText(model.titleText)
        model.lastDrinkText?.let { CaptionText(it, GlanceModifier.defaultWeight(), align = TextAlign.End) }
            ?: Spacer(GlanceModifier.defaultWeight())
    }
}

@Composable
private fun RoomyWideCard(model: WidgetModel, innerWidth: Dp) {
    Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        CardHeader(model)
        Column(modifier = GlanceModifier.fillMaxWidth().progressSemantics(model)) {
            AmountText(model.amountText, 30.sp)
            model.detailLine?.let { CaptionText(it, size = 13.sp) }
            Spacer(GlanceModifier.height(8.dp))
            ProgressBar(model)
        }
        Spacer(GlanceModifier.height(8.dp))
        ButtonRow(model, innerWidth, GlanceModifier.fillMaxWidth())
    }
}

@Composable
private fun DenseWideCard(model: WidgetModel, innerWidth: Dp) {
    val buttonsWidth = (innerWidth * BUTTON_AREA_SHARE)
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = GlanceModifier.defaultWeight().progressSemantics(model)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DropIcon(14.dp)
                Spacer(GlanceModifier.width(4.dp))
                HeadingText(model.titleText, size = 13.sp)
                model.lastDrinkText?.let {
                    Spacer(GlanceModifier.width(8.dp))
                    CaptionText(it, size = 11.sp)
                }
            }
            AmountText(model.amountText, 24.sp)
            model.detailLine?.let { CaptionText(it, size = 12.sp) }
            Spacer(GlanceModifier.height(4.dp))
            ProgressBar(model)
        }
        Spacer(GlanceModifier.width(12.dp))
        ButtonRow(model, buttonsWidth, GlanceModifier.width(buttonsWidth))
    }
}

internal val MIN_LABEL_SIZE = 10.sp
private const val BUTTON_AREA_SHARE = 0.52f

/** The buttons side by side, sharing [rowWidth]; the label shrinks so that three fit. */
@Composable
internal fun ButtonRow(model: WidgetModel, rowWidth: Dp, modifier: GlanceModifier) {
    val count = model.buttons.size
    val each = (rowWidth - ButtonGap * (count - 1)) / count
    fun available(button: WidgetButton) =
        each - ButtonPadding * 2 - (if (button.icon != null) ButtonIconSize + ButtonIconGap else 0.dp) - ButtonSlack
    // One style for the whole row: if any label is too long to fit beside its icon, they all lose their unit.
    val short = model.buttons.any { !fits(it.label, available(it), MIN_LABEL_SIZE) }
    Row(modifier = modifier) {
        model.buttons.forEachIndexed { index, button ->
            if (index > 0) Spacer(GlanceModifier.width(ButtonGap))
            val available = available(button)
            AddButton(
                button = button,
                modifier = GlanceModifier.defaultWeight(),
                showIcon = true,
                short = short,
                textSize = fittedSize(if (short) button.shortLabel else button.label, available, 13.sp),
                horizontalPadding = ButtonPadding,
                fillWidth = true,
            )
        }
    }
}
