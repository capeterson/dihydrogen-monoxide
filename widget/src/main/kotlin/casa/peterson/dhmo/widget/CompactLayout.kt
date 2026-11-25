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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width

/** Below this height there is no room to stack the button under the amount, so they sit side by side. */
private val StackedMinHeight = 70.dp

/** 2x1: "1.25 / 2.5 L", a thin progress bar and one add button. */
@Composable
internal fun CompactLayout(model: WidgetModel) {
    val stacked = LocalSize.current.height >= StackedMinHeight
    WidgetFrame(PaddingValues(horizontal = 12.dp, vertical = if (stacked) 8.dp else 1.dp)) {
        if (model.needsSetup) {
            SetupContent(model, narrow = false)
        } else if (stacked) {
            StackedCompact(model)
        } else {
            SideBySideCompact(model)
        }
    }
}

@Composable
private fun StackedCompact(model: WidgetModel) {
    Column(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Column(modifier = GlanceModifier.fillMaxWidth().progressSemantics(model)) {
            AmountText(model.compactText, fittedSize(model.compactText, LocalSize.current.width - 24.dp, 15.sp, 8.sp))
            Spacer(GlanceModifier.height(6.dp))
            ProgressBar(model)
        }
        model.primaryButton?.let { AddButton(it, GlanceModifier.fillMaxWidth(), fillWidth = true) }
    }
}

@Composable
private fun SideBySideCompact(model: WidgetModel) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = GlanceModifier.defaultWeight().progressSemantics(model)) {
            AmountText(model.compactText, fittedSize(model.compactText, LocalSize.current.width - 100.dp, 14.sp, 8.sp))
            Spacer(GlanceModifier.height(4.dp))
            ProgressBar(model)
        }
        Spacer(GlanceModifier.width(8.dp))
        model.primaryButton?.let { AddButton(it, short = true) }
    }
}
