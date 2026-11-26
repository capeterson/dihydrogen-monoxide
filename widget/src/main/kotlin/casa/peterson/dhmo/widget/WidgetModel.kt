package casa.peterson.dhmo.widget

import android.content.res.Resources
import casa.peterson.dhmo.core.data.usecase.today.TodaySnapshot
import casa.peterson.dhmo.core.domain.format.VolumeFormatter
import casa.peterson.dhmo.core.domain.pace.PaceStatus
import casa.peterson.dhmo.core.model.Container
import casa.peterson.dhmo.core.model.ContainerIcon
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * One coaster. A null [containerId] means there is no container yet: the coaster reads "Add" and opens the app, where
 * containers are managed, instead of logging.
 */
internal data class WidgetButton(
    val containerId: UUID?,
    /** "Glass", or "Add" when there is no container. */
    val name: String,
    /** `250 ml`; null when there is no container. */
    val amountText: String?,
    /** `250` and `ml` apart, for coasters too narrow for the whole amount on one line. */
    val amountValue: String?,
    val unitSymbol: String?,
    val icon: ContainerIcon?,
    val contentDescription: String,
)

/** How the status line is coloured: water (teal) for pace, lamplight (amber) for a met goal. */
internal enum class StatusTone { WATER, LAMPLIGHT }

/** A drink just logged from the widget, which can still be taken back. */
internal data class WidgetUndo(
    val entryId: UUID,
    /** `Added 250 ml`. */
    val text: String,
    val description: String,
)

/** Everything any widget layout shows, already formatted. The layouts add no logic of their own. */
internal data class WidgetModel(
    /** Onboarding is unfinished: the widget only invites the user to open the app. */
    val needsSetup: Boolean,
    /** Draw with the night scheme. Resolved from the app's own Day / Night / Follow system choice. */
    val night: Boolean,
    /** The system font scale the widget is drawn for; each layout caps how far its text follows it (see [maxFontScale]). */
    val fontScale: Float,
    /** "Today". */
    val titleText: String,
    /** `1,250` with a goal (the unit follows in [goalSuffix]), or `1,250 ml` without one, as on the Today screen. */
    val amountNumber: String,
    /** `1,250 ml`. */
    val amountText: String,
    /** `1,250` and `ml` apart, for the narrowest layout, which puts the unit on its own line when there is no goal. */
    val amountValue: String,
    val unitSymbol: String,
    /** `/ 2,500 ml`; null while there is no goal. */
    val goalSuffix: String?,
    /** `of 2,500 ml`, for layouts too narrow for the slash form; null while there is no goal. */
    val goalLine: String?,
    /** 0..1: how full the jar is. */
    val fraction: Float,
    val goalReached: Boolean,
    /** Rain on the window while behind pace, as on the Today screen. */
    val raining: Boolean,
    /** "On track", "250 ml behind pace", "Goal reached"; null without a goal or outside the wake window. */
    val statusText: String?,
    /** The same in fewer words ("250 ml behind"), for the strips. */
    val statusShortText: String?,
    val statusTone: StatusTone,
    /** `Last drink 2:15 PM`; null before the first drink. */
    val lastDrinkText: String?,
    /** What TalkBack reads for the jar or window. */
    val progressDescription: String,
    /** At most [MAX_BUTTONS], the lowest `sortOrder` first. Never empty. */
    val buttons: List<WidgetButton>,
    /** Set for a few seconds after a drink is logged from the widget. */
    val undo: WidgetUndo?,
    val setupTitle: String,
    val setupBody: String,
    val setupAction: String,
    val setupDescription: String,
) {
    /** The first coaster, which the one-coaster layouts show. */
    val primaryButton: WidgetButton get() = buttons.first()

    companion object {
        const val MAX_BUTTONS = 3
    }
}

/** A drink logged from the widget that may still be undone, as read from [WidgetUndoStore]. */
internal data class PendingUndo(val entryId: UUID, val volumeMl: Int)

private const val PERCENT = 100

private const val NO_BREAK_SPACE = '\u00A0'

/** `2,500 ml` or `84.5 fl oz`, never wrapped inside: narrow layouts wrap before the amount, not within it. */
private fun volume(ml: Int, units: VolumeUnit, locale: Locale): String =
    VolumeFormatter.format(ml, units, locale).replace(' ', NO_BREAK_SPACE)
private const val LAST_PERCENT_BEFORE_GOAL = 99

/**
 * Turns the app's state into what the widget shows. Pure: the same arguments always give the same model, so tests and
 * screenshots can feed fixed states.
 */
@Suppress("LongParameterList")
internal fun buildWidgetModel(
    snapshot: TodaySnapshot,
    containers: List<Container>,
    units: VolumeUnit,
    locale: Locale,
    onboarded: Boolean,
    night: Boolean,
    resources: Resources,
    zone: ZoneId,
    is24Hour: Boolean,
    undo: PendingUndo? = null,
): WidgetModel {
    val consumed = snapshot.totalMl
    val goalMl = snapshot.goal?.targetMl?.takeIf { it > 0 }
    val amount = VolumeFormatter.format(consumed, units, locale)
    val goalText = goalMl?.let { volume(it, units, locale) }
    val reached = goalMl != null && consumed >= goalMl
    val percent = when {
        goalMl == null -> 0

        reached -> consumed * PERCENT / goalMl

        // 2,490 of 2,500 is not "100 percent": that is only said once the goal is met.
        else -> (consumed * PERCENT / goalMl).coerceAtMost(LAST_PERCENT_BEFORE_GOAL)
    }
    val status = status(snapshot, reached, goalMl != null, units, locale, resources)
    return WidgetModel(
        needsSetup = !onboarded,
        night = night,
        fontScale = resources.configuration.fontScale,
        titleText = resources.getString(R.string.widget_title_today),
        amountNumber = if (goalMl == null) amount else VolumeFormatter.formatNumber(consumed, units, locale),
        amountText = amount,
        amountValue = VolumeFormatter.formatNumber(consumed, units, locale),
        unitSymbol = VolumeFormatter.symbol(units),
        goalSuffix = goalText?.let { resources.getString(R.string.widget_goal_suffix, it) },
        goalLine = goalText?.let { resources.getString(R.string.widget_goal_of, it) },
        fraction = if (goalMl == null) 0f else (consumed.toFloat() / goalMl).coerceIn(0f, 1f),
        goalReached = reached,
        raining = goalMl != null && !reached && snapshot.pace?.status == PaceStatus.BEHIND,
        statusText = status?.long,
        statusShortText = status?.short,
        statusTone = if (reached) StatusTone.LAMPLIGHT else StatusTone.WATER,
        lastDrinkText = lastDrinkText(snapshot, locale, zone, is24Hour, resources),
        progressDescription = progressDescription(resources, amount, goalText, percent, reached),
        buttons = buttons(containers, units, locale, resources),
        undo = undo?.let {
            val volume = VolumeFormatter.format(it.volumeMl, units, locale)
            WidgetUndo(
                it.entryId,
                resources.getString(R.string.widget_added, volume),
                resources.getString(R.string.widget_undo_description, volume),
            )
        },
        setupTitle = resources.getString(R.string.widget_setup_title),
        setupBody = resources.getString(R.string.widget_setup_body),
        setupAction = resources.getString(R.string.widget_setup_action),
        setupDescription = resources.getString(R.string.widget_setup_description),
    )
}

private fun buttons(containers: List<Container>, units: VolumeUnit, locale: Locale, resources: Resources): List<WidgetButton> =
    containers.sortedBy { it.sortOrder }.take(WidgetModel.MAX_BUTTONS).map { container ->
        val volume = volume(container.volumeMl, units, locale)
        WidgetButton(
            containerId = container.id,
            name = container.name,
            amountText = volume,
            amountValue = VolumeFormatter.formatNumber(container.volumeMl, units, locale),
            unitSymbol = VolumeFormatter.symbol(units),
            icon = container.icon,
            contentDescription = resources.getString(R.string.widget_add_description, container.name, volume),
        )
    }.ifEmpty {
        listOf(
            WidgetButton(
                null,
                resources.getString(R.string.widget_add),
                null,
                null,
                null,
                null,
                resources.getString(R.string.widget_add_container_description),
            ),
        )
    }

private class Status(val long: String, val short: String)

@Suppress("LongParameterList")
private fun status(
    snapshot: TodaySnapshot,
    reached: Boolean,
    hasGoal: Boolean,
    units: VolumeUnit,
    locale: Locale,
    resources: Resources,
): Status? {
    val pace = snapshot.pace
    return when {
        reached -> resources.getString(R.string.widget_goal_reached).let { Status(it, it) }

        pace == null || !hasGoal -> null

        pace.status == PaceStatus.AHEAD ->
            Status(resources.getString(R.string.widget_pace_ahead), resources.getString(R.string.widget_pace_ahead_short))

        pace.status == PaceStatus.ON_TRACK -> resources.getString(R.string.widget_pace_on_track).let { Status(it, it) }

        else -> {
            val deficit = volume(pace.deficitMl.coerceAtLeast(0), units, locale)
            Status(
                resources.getString(R.string.widget_pace_behind, deficit),
                resources.getString(R.string.widget_pace_behind_short, deficit),
            )
        }
    }
}

private fun lastDrinkText(snapshot: TodaySnapshot, locale: Locale, zone: ZoneId, is24Hour: Boolean, resources: Resources): String? {
    val latest = snapshot.entries.maxByOrNull { it.instant } ?: return null
    val pattern = if (is24Hour) "HH:mm" else "h:mm a"
    val time = DateTimeFormatter.ofPattern(pattern, locale).format(latest.instant.atZone(zone))
    return resources.getString(R.string.widget_last_drink, time)
}

private fun progressDescription(resources: Resources, amount: String, goalText: String?, percent: Int, reached: Boolean): String = when {
    goalText == null -> resources.getString(R.string.widget_no_goal_description, amount)
    reached -> resources.getString(R.string.widget_goal_reached_description, amount, goalText)
    else -> resources.getQuantityString(R.plurals.widget_progress_description, percent, amount, goalText, percent)
}
