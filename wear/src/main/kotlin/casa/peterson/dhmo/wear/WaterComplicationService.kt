package casa.peterson.dhmo.wear

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import casa.peterson.dhmo.core.wear.WearContainer
import casa.peterson.dhmo.core.wear.WearState

/** Progress as a ranged value or short text; a tap logs the default container. */
class WaterComplicationService : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? = data(
        type,
        WearState("2026-01-01", PREVIEW_TOTAL, PREVIEW_GOAL, containers = listOf(WearContainer("Glass", WearState.DEFAULT_ML))),
        null,
    )

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val tap = PendingIntent.getBroadcast(
            this,
            0,
            Intent(this, LogDefaultReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return data(request.complicationType, WearGraph.store(this).read(), tap)
    }

    private fun data(type: ComplicationType, state: WearState?, tap: PendingIntent?): ComplicationData? {
        val model = WaterTileModel.from(state, resources.configuration.locales[0])
        val total =
            model.goal?.let { getString(R.string.wear_progress_of_goal, model.total, it) }
                ?: getString(R.string.wear_progress_no_goal, model.total)
        val description = PlainComplicationText.Builder(getString(R.string.wear_progress_description, model.percent, total)).build()
        val percent = PlainComplicationText.Builder("${model.percent}%").build()
        val image = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.wear_ic_water_drop)).build()
        return when (type) {
            ComplicationType.RANGED_VALUE -> {
                val goal = state?.goalMl?.takeIf { it > 0 } ?: 1
                RangedValueComplicationData.Builder((state?.totalMl ?: 0).coerceAtMost(goal).toFloat(), 0f, goal.toFloat(), description)
                    .setText(percent)
                    .setMonochromaticImage(image)
                    .setTapAction(tap)
                    .build()
            }

            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(percent, description)
                .setMonochromaticImage(image)
                .setTapAction(tap)
                .build()

            else -> null
        }
    }

    private companion object {
        const val PREVIEW_TOTAL = 1250
        const val PREVIEW_GOAL = 2000
    }
}
