package casa.peterson.dhmo.core.model

import casa.peterson.dhmo.core.model.settings.AdjustmentToggles
import java.time.LocalDate
import kotlinx.serialization.Serializable

data class Profile(
    val birthDate: LocalDate,
    val gender: Gender,
    val weightKg: Double,
    val heightCm: Double?,
    val activityLevel: ActivityLevel,
    val climate: Climate,
    val pregnancyStatus: PregnancyStatus,
    val lactating: Boolean,
    val highAltitude: Boolean,
)

data class ExerciseSession(val minutes: Int, val intensity: Intensity)

sealed interface ActivityInput {
    data class Level(val level: ActivityLevel) : ActivityInput

    data class Sessions(val sessions: List<ExerciseSession>) : ActivityInput
}

sealed interface ClimateInput {
    data class Manual(val climate: Climate, val forecastUnavailable: Boolean = false) : ClimateInput

    data class Forecast(val maxApparentTempC: Double) : ClimateInput
}

data class GoalInput(
    /** The hydration day; age is computed on this date. */
    val date: LocalDate,
    val profile: Profile,
    val activity: ActivityInput,
    val climate: ClimateInput,
    val toggles: AdjustmentToggles,
)

@Serializable
enum class ComponentKind {
    BASE_CHILD_TABLE,
    BASE_WEIGHT,
    BASE_GENDER_FLOOR,
    PREGNANCY,
    LACTATION,
    ACTIVITY_LEVEL,
    ACTIVITY_MINUTES,
    CLIMATE_MANUAL,
    CLIMATE_FORECAST,
    ALTITUDE,
}

@Serializable
data class GoalComponent(
    val kind: ComponentKind,
    /** The value this component would add. */
    val ml: Int,
    /** False when its toggle is off; such components are listed but not summed. */
    val applied: Boolean,
    val detail: Map<String, String> = emptyMap(),
)

@Serializable
data class GoalBreakdown(
    val policyVersion: Int,
    val ageYears: Int,
    /** Fixed order: base, pregnancy, lactation, activity, climate, altitude. */
    val components: List<GoalComponent>,
    /** Sum of applied components. */
    val rawMl: Int,
    val clampedMl: Int,
    /** `roundTo50(clampedMl)`. */
    val calculatedMl: Int,
    val clamp: ClampFlag,
)

data class ResolvedGoal(val targetMl: Int, val calculatedMl: Int?, val mode: GoalMode)
