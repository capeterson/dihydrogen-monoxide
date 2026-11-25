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

/** Below this height the percentage moves up beside the amount to leave room for the button. */
private val RoomyMinHeight = 150.dp

/** 2x2: amount and percentage stacked, a progress bar and one full-width add button. */
@Composable
internal fun SquareLayout(model: WidgetModel) {
    val roomy = LocalSize.current.height >= RoomyMinHeight
    WidgetFrame(PaddingValues(horizontal = 12.dp, vertical = if (roomy) 10.dp else 4.dp)) {
        if (model.needsSetup) {
            SetupContent(model, narrow = false)
        } else {
            Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = GlanceModifier.fillMaxWidth().progressSemantics(model)) {
                    if (roomy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DropIcon(16.dp)
                            Spacer(GlanceModifier.width(4.dp))
                            HeadingText(model.titleText, size = 13.sp)
                        }
                        Spacer(GlanceModifier.height(6.dp))
                        AmountText(model.amountText, fittedSize(model.amountText, LocalSize.current.width - 24.dp, 20.sp))
                        model.percentLine()?.let { CaptionText(it, size = 13.sp) }
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            AmountText(model.amountText, fittedSize(model.amountText, LocalSize.current.width - 24.dp, 18.sp))
                            model.percentText?.let {
                                Spacer(GlanceModifier.width(8.dp))
                                CaptionText(it, size = 13.sp)
                            }
                        }
                    }
                    Spacer(GlanceModifier.height(if (roomy) 8.dp else 4.dp))
                    ProgressBar(model)
                }
                Spacer(GlanceModifier.height(if (roomy) 8.dp else 2.dp))
                model.primaryButton?.let { AddButton(it, GlanceModifier.fillMaxWidth(), fillWidth = true) }
            }
        }
    }
}

/** `50% of 2.5 L`. */
private fun WidgetModel.percentLine(): String? = listOfNotNull(percentText, goalLine).joinToString(" ").ifEmpty { null }
