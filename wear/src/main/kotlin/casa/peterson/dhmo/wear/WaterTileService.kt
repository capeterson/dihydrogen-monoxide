package casa.peterson.dhmo.wear

import android.content.Context
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.core.content.ContextCompat
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.CircularProgressIndicator
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.ProgressIndicatorColors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.EdgeContentLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import java.time.Clock
import kotlinx.coroutines.Dispatchers

/**
 * The tile: a progress ring against today's goal with the total, and quick-add chips for the top containers. A chip
 * tap reloads the tile with its clickable id, which logs the drink before the new layout is built.
 */
class WaterTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture(Dispatchers.Default) {
            val context = this@WaterTileService
            val store = WearGraph.store(context)
            WaterTileModel.volumeForClick(requestParams.currentState.lastClickableId)?.let { volume ->
                // This request redraws the tile itself; only the complications need a refresh.
                val complicationsOnly = SurfaceRefresher { WearGraph.complicationRefresher(context).refresh() }
                WaterLogger(store, WearGraph.link(context), complicationsOnly, Clock.systemUTC()).log(volume)
            }
            val state = store.read()
            if (state == null) runCatching { WearGraph.link(context).requestState() }
            val model = WaterTileModel.from(state, context.resources.configuration.locales[0])
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(tileLayout(context, model, requestParams.deviceConfiguration)))
                .build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        SuspendToFutureAdapter.launchFuture { ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build() }

    private companion object {
        const val RESOURCES_VERSION = "1"
    }
}

internal fun tileLayout(context: Context, model: WaterTileModel, device: DeviceParameters): LayoutElementBuilders.LayoutElement {
    val total = if (model.goal != null) {
        context.getString(R.string.wear_progress_of_goal, model.total, model.goal)
    } else {
        context.getString(R.string.wear_progress_no_goal, model.total)
    }
    val primary = ContextCompat.getColor(context, R.color.wear_primary)
    val onBackground = argb(ContextCompat.getColor(context, R.color.wear_on_background))
    val muted = argb(ContextCompat.getColor(context, R.color.wear_on_surface_variant))
    val track = ContextCompat.getColor(context, R.color.wear_surface_variant)
    val ring = CircularProgressIndicator.Builder()
        .setCircularProgressIndicatorColors(ProgressIndicatorColors(primary, track))
        .setProgress(model.progress)
        .setContentDescription(context.getString(R.string.wear_progress_description, model.percent, total))
        .build()
    val row = LayoutElementBuilders.Row.Builder()
    model.buttons.forEachIndexed { index, button ->
        if (index > 0) row.addContent(LayoutElementBuilders.Spacer.Builder().setWidth(dp(CHIP_GAP_DP)).build())
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId(button.clickId)
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()
        row.addContent(
            CompactChip.Builder(context, context.getString(R.string.wear_add_button, button.label), clickable, device)
                .setChipColors(ChipColors(primary, ContextCompat.getColor(context, R.color.wear_on_primary)))
                .build(),
        )
    }
    val content: LayoutElementBuilders.LayoutElement = if (model.connected) {
        row.build()
    } else {
        Text.Builder(
            context,
            context.getString(R.string.wear_waiting_for_phone),
        ).setTypography(Typography.TYPOGRAPHY_CAPTION2).setColor(muted).setMaxLines(2).build()
    }
    val layout = EdgeContentLayout.Builder(device)
        .setResponsiveContentInsetEnabled(true)
        .setEdgeContent(ring)
        .setPrimaryLabelTextContent(
            Text.Builder(context, "${model.percent}%").setTypography(Typography.TYPOGRAPHY_CAPTION1).setColor(onBackground).build(),
        )
        .setContent(
            LayoutElementBuilders.Column.Builder()
                .addContent(totalText(context, total, onBackground))
                .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(CHIP_GAP_DP)).build())
                .addContent(content)
                .build(),
        )
        .build()
    return onNightBackground(context, layout)
}

/** The Night background behind everything, so the tile is the app's palette and not the watch's default black. */
private fun onNightBackground(context: Context, content: LayoutElementBuilders.LayoutElement): LayoutElementBuilders.LayoutElement {
    val background = ModifiersBuilders.Background.Builder().setColor(argb(ContextCompat.getColor(context, R.color.wear_background))).build()
    return LayoutElementBuilders.Box.Builder()
        .setWidth(expand())
        .setHeight(expand())
        .setModifiers(ModifiersBuilders.Modifiers.Builder().setBackground(background).build())
        .addContent(content)
        .build()
}

private fun totalText(context: Context, total: String, color: androidx.wear.protolayout.ColorBuilders.ColorProp) =
    Text.Builder(context, total).setTypography(Typography.TYPOGRAPHY_BODY1).setColor(color).setMaxLines(2).build()

private const val CHIP_GAP_DP = 6f
