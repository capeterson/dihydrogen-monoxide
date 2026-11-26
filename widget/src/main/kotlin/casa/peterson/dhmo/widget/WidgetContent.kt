package casa.peterson.dhmo.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.material3.ColorProviders
import casa.peterson.dhmo.core.designsystem.theme.DarkColors
import casa.peterson.dhmo.core.designsystem.theme.LightColors

/**
 * The widget's root. It uses one scheme, Day or Night, already resolved from the app's own theme setting (see
 * [resolveNight]), because the drawings are bitmaps and have to match the text around them.
 */
@Composable
internal fun WidgetRoot(model: WidgetModel, art: WidgetArt) {
    GlanceTheme(colors = ColorProviders(if (model.night) DarkColors else LightColors)) {
        val layout = layoutForBreakpoint(LocalSize.current)
        CompositionLocalProvider(LocalTextScale provides textScaleFor(layout, model.fontScale)) {
            WidgetContent(model, art, layout)
        }
    }
}

/** Draws [model] with [layout]. Stateless, so tests and screenshots can supply a fixed model and layout. */
@Composable
internal fun WidgetContent(model: WidgetModel, art: WidgetArt, layout: WidgetLayout) {
    WidgetSurface(model) {
        if (model.needsSetup) {
            val space = when (layout) {
                WidgetLayout.TALL_STRIP -> SetupSpace.NARROW
                WidgetLayout.COMPACT, WidgetLayout.WIDE_STRIP -> SetupSpace.SHORT
                else -> SetupSpace.ROOMY
            }
            SetupContent(model, space)
            return@WidgetSurface
        }
        when (layout) {
            WidgetLayout.COMPACT -> CompactLayout(model, art)
            WidgetLayout.WIDE_STRIP -> WideStripLayout(model, art)
            WidgetLayout.TALL_STRIP -> TallStripLayout(model, art)
            WidgetLayout.SQUARE -> SquareLayout(model, art)
            WidgetLayout.WIDE_CARD -> WideCardLayout(model, art)
            WidgetLayout.TALL_CARD -> TallCardLayout(model, art)
        }
    }
}
