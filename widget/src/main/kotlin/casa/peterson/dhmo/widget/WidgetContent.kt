package casa.peterson.dhmo.widget

import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.material3.ColorProviders
import casa.peterson.dhmo.core.designsystem.theme.DarkColors
import casa.peterson.dhmo.core.designsystem.theme.LightColors

/** The widget's themed root: the app's own light and dark schemes, and the layout that belongs to the size Glance composes for. */
@Composable
internal fun WidgetRoot(model: WidgetModel) {
    GlanceTheme(colors = ColorProviders(light = LightColors, dark = DarkColors)) {
        WidgetContent(model, layoutForBreakpoint(LocalSize.current))
    }
}

/** Draws [model] with [layout]. Stateless, so tests and screenshots can supply a fixed model and layout. */
@Composable
internal fun WidgetContent(model: WidgetModel, layout: WidgetLayout) {
    when (layout) {
        WidgetLayout.COMPACT -> CompactLayout(model)
        WidgetLayout.WIDE_STRIP -> WideStripLayout(model)
        WidgetLayout.TALL_STRIP -> TallStripLayout(model)
        WidgetLayout.SQUARE -> SquareLayout(model)
        WidgetLayout.WIDE_CARD -> WideCardLayout(model)
        WidgetLayout.TALL_CARD -> TallCardLayout(model)
    }
}
