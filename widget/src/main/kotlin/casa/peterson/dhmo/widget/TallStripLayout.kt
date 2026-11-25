package casa.peterson.dhmo.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.TextAlign

private const val NO_BREAK_SPACE = '\u00A0'

/** 1x3: the wide strip stood on end. Drop, amount, "of 2.5 L", a water level that fills the middle, and the add button. */
@Composable
internal fun TallStripLayout(model: WidgetModel) {
    WidgetFrame(PaddingValues(horizontal = 6.dp, vertical = 8.dp)) {
        if (model.needsSetup) {
            SetupContent(model, narrow = true)
        } else {
            Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(
                    modifier = GlanceModifier.fillMaxWidth().progressSemantics(model),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DropIcon(18.dp)
                    Spacer(GlanceModifier.height(4.dp))
                    // The number and its unit on two lines: a one-cell-wide column has no room for "1.25 L" at a readable size.
                    val (number, unit) = model.amountText.split(NO_BREAK_SPACE, limit = 2).let { it[0] to it.getOrNull(1) }
                    AmountText(number, 16.sp, align = TextAlign.Center)
                    unit?.let { CaptionText(it, size = 11.sp, align = TextAlign.Center) }
                    model.goalLine?.let { CaptionText(it, size = 10.sp, align = TextAlign.Center) }
                }
                Spacer(GlanceModifier.height(6.dp))
                if (model.goalText != null) {
                    LevelFill(model, GlanceModifier.defaultWeight().width(22.dp))
                } else {
                    Spacer(GlanceModifier.defaultWeight())
                }
                Spacer(GlanceModifier.height(6.dp))
                model.primaryButton?.let {
                    AddButton(
                        it,
                        GlanceModifier.fillMaxWidth(),
                        short = true,
                        textSize = fittedSize(
                            it.shortLabel,
                            LocalSize.current.width - 20.dp,
                            13.sp,
                        ),
                        horizontalPadding = 2.dp,
                        fillWidth = true,
                    )
                }
            }
        }
    }
}
