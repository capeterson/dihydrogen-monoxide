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

/** One add button. A null [containerId] means "there is no container yet": the button opens the app instead of logging. */
internal data class WidgetButton(
    val containerId: UUID?,
    /** `+250 ml`, or "Open app". */
    val label: String,
    /** `+250`: the same without the unit, for the narrowest layouts. */
    val shortLabel: String,
    val icon: ContainerIcon?,
    val contentDescription: String,
)

/** Everything any widget layout shows, already formatted. The layouts add no logic of their own. */
internal data class WidgetModel(
    /** Onboarding is unfinished: the widget only invites the user to open the app. */
    val needsSetup: Boolean,
    /** "Today". */
    val titleText: String,
    /** `1.25 L`. */
    val amountText: String,
    /** `2.5 L`; null while there is no goal. */
    val goalText: String?,
    /** `1.25 / 2.5 L`, or just the amount while there is no goal. */
    val compactText: String,
    /** `of 2.5 L`. */
    val goalLine: String?,
    /** `50%`. */
    val percentText: String?,
    val percent: Int,
    /** 0..1, for the progress bar and the level fill. */
    val fraction: Float,
    val goalReached: Boolean,
    /** "On track", "Ahead" or "250 ml behind"; null outside the wake window or without a goal. */
    val paceText: String?,
    /** `last drink 2:15 pm`. */
    val lastDrinkText: String?,
    val progressDescription: String,
    /** At most [MAX_BUTTONS], the lowest `sortOrder` first. Never empty unless [needsSetup]. */
    val buttons: List<WidgetButton>,
    val setupText: String,
    val setupDescription: String,
) {
    /** `of 2.5 L · 50% · 250 ml behind`, with whatever parts exist. */
    val detailLine: String? get() = listOfNotNull(goalLine, percentText, paceText).joinToString(SEPARATOR).ifEmpty { null }

    /** The first button, which the one-button layouts show. */
    val primaryButton: WidgetButton? get() = buttons.firstOrNull()

    companion object {
        const val MAX_BUTTONS = 3
        const val SEPARATOR = " · "
    }
}

private const val PERCENT = 100
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
    resources: Resources,
    zone: ZoneId,
    is24Hour: Boolean,
): WidgetModel {
    val consumed = snapshot.totalMl
    val goalMl = snapshot.goal?.targetMl?.takeIf { it > 0 }
    val amount = VolumeFormatter.format(consumed, units, locale)
    val goalText = goalMl?.let { VolumeFormatter.format(it, units, locale) }
    val reached = goalMl != null && consumed >= goalMl
    val fraction = if (goalMl == null) 0f else (consumed.toFloat() / goalMl).coerceIn(0f, 1f)
    val percent = when {
        goalMl == null -> 0
        reached -> consumed * PERCENT / goalMl
        else -> (consumed * PERCENT / goalMl).coerceAtMost(LAST_PERCENT_BEFORE_GOAL)
    }
    val buttons = containers.sortedBy { it.sortOrder }.take(WidgetModel.MAX_BUTTONS).map { container ->
        val volume = VolumeFormatter.format(container.volumeMl, units, locale)
        WidgetButton(
            containerId = container.id,
            label = resources.getString(R.string.widget_add_label, volume),
            shortLabel = resources.getString(R.string.widget_add_label, VolumeFormatter.formatNumber(container.volumeMl, units, locale)),
            icon = container.icon,
            contentDescription = resources.getString(R.string.widget_add_description, container.name, volume),
        )
    }.ifEmpty {
        val open = resources.getString(R.string.widget_open_app)
        val short = resources.getString(R.string.widget_open_app_short)
        listOf(WidgetButton(null, open, short, null, resources.getString(R.string.widget_open_app_description)))
    }
    return WidgetModel(
        needsSetup = !onboarded,
        titleText = resources.getString(R.string.widget_title_today),
        amountText = amount,
        goalText = goalText,
        compactText = if (goalMl == null) amount else compactText(consumed, goalMl, units, locale),
        goalLine = goalText?.let { resources.getString(R.string.widget_goal_of, it) },
        percentText = goalMl?.let { resources.getString(R.string.widget_percent, percent) },
        percent = percent,
        fraction = fraction,
        goalReached = reached,
        paceText = if (goalMl == null) null else paceText(snapshot, units, locale, resources),
        lastDrinkText = lastDrinkText(snapshot, locale, zone, is24Hour, resources),
        progressDescription = progressDescription(resources, amount, goalText, percent, reached),
        buttons = buttons,
        setupText = resources.getString(R.string.widget_setup),
        setupDescription = resources.getString(R.string.widget_setup_description),
    )
}

/** `1.25 / 2.5 L`: the unit is said once, when both numbers share it. */
private fun compactText(consumedMl: Int, goalMl: Int, units: VolumeUnit, locale: Locale): String =
    VolumeFormatter.formatNumber(consumedMl, units, locale) + " / " + VolumeFormatter.format(goalMl, units, locale)

private fun paceText(snapshot: TodaySnapshot, units: VolumeUnit, locale: Locale, resources: Resources): String? =
    snapshot.pace?.let { pace ->
        when (pace.status) {
            PaceStatus.AHEAD -> resources.getString(R.string.widget_pace_ahead)

            PaceStatus.ON_TRACK -> resources.getString(R.string.widget_pace_on_track)

            PaceStatus.BEHIND ->
                resources.getString(R.string.widget_pace_behind, VolumeFormatter.format(pace.deficitMl.coerceAtLeast(0), units, locale))
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
