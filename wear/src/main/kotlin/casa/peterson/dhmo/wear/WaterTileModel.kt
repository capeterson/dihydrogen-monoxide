package casa.peterson.dhmo.wear

import casa.peterson.dhmo.core.domain.format.VolumeFormatter
import casa.peterson.dhmo.core.wear.WearState
import java.util.Locale
import kotlin.math.roundToInt

/** What the tile and the complication show, independent of the rendering libraries so it can be unit-tested. */
data class WaterTileModel(
    val progress: Float,
    val percent: Int,
    val total: String,
    val goal: String?,
    val buttons: List<AddButton>,
    val connected: Boolean,
) {
    data class AddButton(val volumeMl: Int, val label: String, val clickId: String)

    companion object {
        private const val CLICK_PREFIX = "add_"
        private const val PERCENT = 100
        private const val TILE_BUTTONS = 2

        fun from(state: WearState?, locale: Locale): WaterTileModel {
            if (state == null) return WaterTileModel(0f, 0, "–", null, emptyList(), connected = false)
            val unit = state.unit
            val volumes = state.containers.map {
                it.volumeMl
            }.ifEmpty { listOf(state.defaultContainer.volumeMl) }.distinct().take(TILE_BUTTONS)
            return WaterTileModel(
                progress = state.progress,
                percent = (state.progress * PERCENT).roundToInt(),
                total = VolumeFormatter.format(state.totalMl, unit, locale),
                goal = state.goalMl.takeIf { it > 0 }?.let { VolumeFormatter.format(it, unit, locale) },
                buttons = volumes.map { AddButton(it, VolumeFormatter.format(it, unit, locale), CLICK_PREFIX + it) },
                connected = true,
            )
        }

        /** The volume of a tapped add button, from the tile's last clickable id. */
        fun volumeForClick(clickId: String?): Int? =
            clickId?.takeIf { it.startsWith(CLICK_PREFIX) }?.removePrefix(CLICK_PREFIX)?.toIntOrNull()?.takeIf { it in 1..MAX_ML }

        private const val MAX_ML = 10_000
    }
}
