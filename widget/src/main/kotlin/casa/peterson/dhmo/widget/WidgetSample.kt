package casa.peterson.dhmo.widget

import android.content.res.Resources
import casa.peterson.dhmo.core.data.usecase.today.TodaySnapshot
import casa.peterson.dhmo.core.domain.pace.Pace
import casa.peterson.dhmo.core.domain.pace.PaceStatus
import casa.peterson.dhmo.core.model.ClampFlag
import casa.peterson.dhmo.core.model.Container
import casa.peterson.dhmo.core.model.ContainerIcon
import casa.peterson.dhmo.core.model.DailyGoal
import casa.peterson.dhmo.core.model.GoalBreakdown
import casa.peterson.dhmo.core.model.GoalMode
import casa.peterson.dhmo.core.model.IntakeEntry
import casa.peterson.dhmo.core.model.Source
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID

/** The fixed states the picker preview, the render tests and the screenshots draw. */
internal enum class WidgetSample {
    BEHIND,
    ON_TRACK,
    AHEAD,
    OUTSIDE_WAKE_WINDOW,
    GOAL_REACHED,
    NO_GOAL,
    NO_CONTAINERS,
    FEW_CONTAINERS,
    ONBOARDING,

    /** Behind pace, a moment after a glass was logged from the widget: Undo is offered. */
    JUST_ADDED,
}

private const val GOAL_ML = 2500
private const val HALF_ML = 1250
private const val EXPECTED_ML = 1500
private const val GLASS_ML = 250
private const val BEHIND_ML = 250
private const val AHEAD_TOTAL_ML = 1750
private const val REACHED_ML = 2600
private const val NO_GOAL_ML = 750

private val SampleDay = LocalDate.of(2026, 6, 10)
private val SampleEntryId = UUID.fromString("00000000-0000-0000-0000-0000000000aa")
private val SampleContainers = listOf(
    Container(UUID.fromString("00000000-0000-0000-0000-000000000001"), "Glass", 250, ContainerIcon.GLASS, 0, Instant.EPOCH),
    Container(UUID.fromString("00000000-0000-0000-0000-000000000002"), "Mug", 350, ContainerIcon.MUG, 1, Instant.EPOCH),
    Container(UUID.fromString("00000000-0000-0000-0000-000000000003"), "Bottle", 500, ContainerIcon.BOTTLE, 2, Instant.EPOCH),
)

private fun sampleGoal(targetMl: Int) = DailyGoal(
    date = SampleDay,
    targetMl = targetMl,
    calculatedMl = targetMl,
    mode = GoalMode.CALCULATED,
    breakdown = GoalBreakdown(1, 0, emptyList(), 0, 0, 0, ClampFlag.NONE),
    dayStartMinutes = 0,
    policyVersion = 1,
    updatedAt = Instant.EPOCH,
)

private fun sampleEntry(totalMl: Int): List<IntakeEntry> = if (totalMl == 0) {
    emptyList()
} else {
    val at = Instant.parse("2026-06-10T14:15:00Z")
    listOf(
        IntakeEntry(
            id = SampleEntryId,
            instant = at,
            zoneOffset = ZoneOffset.UTC,
            hydrationDay = SampleDay,
            volumeMl = GLASS_ML,
            source = Source.WIDGET,
            createdAt = at,
            updatedAt = at,
        ),
    )
}

/** A model for [sample], built the same way the widget builds its own. */
internal fun sampleModel(
    resources: Resources,
    sample: WidgetSample,
    units: VolumeUnit = VolumeUnit.ML,
    locale: Locale = Locale.US,
    night: Boolean = false,
): WidgetModel {
    val (total, goal, pace) = when (sample) {
        WidgetSample.BEHIND, WidgetSample.FEW_CONTAINERS, WidgetSample.NO_CONTAINERS, WidgetSample.ONBOARDING, WidgetSample.JUST_ADDED ->
            Triple(HALF_ML, sampleGoal(GOAL_ML), Pace(EXPECTED_ML, BEHIND_ML, PaceStatus.BEHIND))

        WidgetSample.ON_TRACK -> Triple(EXPECTED_ML, sampleGoal(GOAL_ML), Pace(EXPECTED_ML, 0, PaceStatus.ON_TRACK))

        WidgetSample.AHEAD -> Triple(AHEAD_TOTAL_ML, sampleGoal(GOAL_ML), Pace(EXPECTED_ML, -BEHIND_ML, PaceStatus.AHEAD))

        WidgetSample.OUTSIDE_WAKE_WINDOW -> Triple(HALF_ML, sampleGoal(GOAL_ML), null)

        WidgetSample.GOAL_REACHED -> Triple(REACHED_ML, sampleGoal(GOAL_ML), Pace(GOAL_ML, GOAL_ML - REACHED_ML, PaceStatus.AHEAD))

        WidgetSample.NO_GOAL -> Triple(NO_GOAL_ML, null, null)
    }
    val containers = when (sample) {
        WidgetSample.NO_CONTAINERS -> emptyList()
        WidgetSample.FEW_CONTAINERS -> SampleContainers.take(2)
        else -> SampleContainers
    }
    return buildWidgetModel(
        snapshot = TodaySnapshot(SampleDay, sampleEntry(total), total, goal, pace),
        containers = containers,
        units = units,
        locale = locale,
        onboarded = sample != WidgetSample.ONBOARDING,
        night = night,
        resources = resources,
        zone = ZoneOffset.UTC,
        is24Hour = false,
        undo = if (sample == WidgetSample.JUST_ADDED) PendingUndo(SampleEntryId, GLASS_ML) else null,
    )
}
