package casa.peterson.dhmo.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width

/** 3x1: the water drop, "1.25 L of 2.5 L" over a progress bar, and the default container's add button on the right. */
@Composable
internal fun WideStripLayout(model: WidgetModel) {
    WidgetFrame(PaddingValues(horizontal = 12.dp, vertical = 1.dp)) {
        if (model.needsSetup) {
            SetupContent(model, narrow = false)
        } else {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DropIcon(20.dp)
                Spacer(GlanceModifier.width(8.dp))
                Column(modifier = GlanceModifier.defaultWeight().progressSemantics(model)) {
                    AmountText(model.amountText, 16.sp)
                    model.goalLine?.let { CaptionText(it, size = 11.sp) }
                    Spacer(GlanceModifier.height(3.dp))
                    ProgressBar(model)
                }
                Spacer(GlanceModifier.width(8.dp))
                model.primaryButton?.let { AddButton(it, short = true) }
            }
        }
    }
}
