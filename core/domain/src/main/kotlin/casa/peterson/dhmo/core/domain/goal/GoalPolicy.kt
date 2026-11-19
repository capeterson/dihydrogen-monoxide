@file:Suppress("MagicNumber") // this file is the constants table of TECHNICAL_PLAN §7.2 (v1) and §7.6 (v2)

package casa.peterson.dhmo.core.domain.goal

import casa.peterson.dhmo.core.model.ActivityLevel
import casa.peterson.dhmo.core.model.Climate
import casa.peterson.dhmo.core.model.Gender
import casa.peterson.dhmo.core.model.Intensity

/** The constants of one version of the goal formula (TECHNICAL_PLAN §7.2). */
interface GoalPolicy {
    val version: Int

    /** Used only when there is neither a profile nor an override. */
    val fallbackTargetMl: Int

    /** Completed age from which the weight-based formula applies; below it the child table applies. */
    val adultAgeYears: Int

    /** Drinks-only child base value for the age (0..13) and gender. */
    fun childBaseMl(ageYears: Int, gender: Gender): Int

    /** Label of the child age band for the age, as used in `detail["ageBand"]`. */
    fun childAgeBand(ageYears: Int): String

    /** Drinks-only ml per kg of [calculationWeightKg] for adults. */
    fun perKgFactor(ageYears: Int): Int

    /**
     * The weight the per-kg factor is applied to. Equal to [weightKg] unless the policy adjusts it for body
     * composition (see [GoalPolicyV2]); [heightCm] may be null, in which case no adjustment is possible.
     */
    fun calculationWeightKg(weightKg: Double, heightCm: Double?): Double = weightKg

    fun genderFloorMl(gender: Gender): Int

    /** Upper bound for the adult baseline (before adjustments); [Int.MAX_VALUE] when the policy has none. */
    fun baseCapMl(gender: Gender): Int = Int.MAX_VALUE

    val pregnancyMl: Int
    val lactationMl: Int

    fun activityLevelMl(level: ActivityLevel): Int

    fun mlPerMinute(intensity: Intensity): Int

    val activityMinutesCapMl: Int

    fun climateManualMl(climate: Climate): Int

    fun climateForecastMl(maxApparentTempC: Double): Int

    val altitudeMl: Int

    val adultMinMl: Int
    val adultMaxMl: Int
    val childMinMl: Int
    val childMaxMl: Int
}

/** The policy new goals are calculated with. Older snapshots keep the version they were written with. */
val CurrentGoalPolicy: GoalPolicy get() = GoalPolicyV2

/**
 * Superseded by [GoalPolicyV2]. Kept so the version number of historical snapshots still names real constants.
 *
 * Its adult base multiplied 35 ml/kg by total body weight. That figure is a *total* water (clinical maintenance)
 * heuristic, yet it was used as drinks-only without the ×0.8 the child table and floors use, and it scaled linearly
 * with weight, so it badly overestimated for large bodies (120 kg → 4.2 L base before any adjustment).
 */
object GoalPolicyV1 : GoalPolicy {
    override val version: Int = 1
    override val fallbackTargetMl: Int = 2000
    override val adultAgeYears: Int = 14

    override fun childBaseMl(ageYears: Int, gender: Gender): Int = when (ageYears) {
        0 -> 720

        1 -> 920

        2, 3 -> 1040

        in 4..8 -> 1280

        else -> when (gender) {
            Gender.FEMALE -> 1520

            Gender.MALE -> 1680

            // The reference tables only distinguish female and male, so everyone else gets the average of the two.
            Gender.NON_BINARY, Gender.UNSPECIFIED -> 1600
        }
    }

    override fun childAgeBand(ageYears: Int): String = when (ageYears) {
        0 -> "0"
        1 -> "1"
        2, 3 -> "2-3"
        in 4..8 -> "4-8"
        else -> "9-13"
    }

    override fun perKgFactor(ageYears: Int): Int = when {
        ageYears <= 30 -> 35
        ageYears <= 55 -> 33
        ageYears <= 65 -> 30
        else -> 28
    }

    override fun genderFloorMl(gender: Gender): Int = when (gender) {
        Gender.FEMALE -> 1600
        Gender.MALE -> 2000
        Gender.NON_BINARY, Gender.UNSPECIFIED -> 1800
    }

    override val pregnancyMl: Int = 300
    override val lactationMl: Int = 700

    override fun activityLevelMl(level: ActivityLevel): Int = when (level) {
        ActivityLevel.SEDENTARY -> 0
        ActivityLevel.LIGHT -> 250
        ActivityLevel.MODERATE -> 500
        ActivityLevel.VERY_ACTIVE -> 750
    }

    override fun mlPerMinute(intensity: Intensity): Int = when (intensity) {
        Intensity.LIGHT -> 6
        Intensity.MODERATE -> 12
        Intensity.VIGOROUS -> 18
    }

    override val activityMinutesCapMl: Int = 2000

    override fun climateManualMl(climate: Climate): Int = when (climate) {
        Climate.TEMPERATE -> 0
        Climate.WARM -> 250
        Climate.HOT_HUMID -> 500
    }

    override fun climateForecastMl(maxApparentTempC: Double): Int = when {
        maxApparentTempC < 25.0 -> 0
        maxApparentTempC < 30.0 -> 250
        maxApparentTempC < 35.0 -> 500
        else -> 750
    }

    override val altitudeMl: Int = 250
    override val adultMinMl: Int = 1000
    override val adultMaxMl: Int = 6000
    override val childMinMl: Int = 600
    override val childMaxMl: Int = 3000
}

/**
 * TECHNICAL_PLAN §7.6. Same structure and adjustments as [GoalPolicyV1]; only the adult (14+) baseline changes:
 *
 * 1. **Drinks only.** The per-kg factors are v1's × 0.8 (rounded down to whole ml), the same "about 20% of water
 *    comes from food" factor (US National Academies 2004) v1 already applied to the child table and the floors.
 * 2. **Adjusted body weight.** Adipose tissue holds far less water than lean tissue, so water needs do not grow in
 *    step with total weight. When height is known and BMI is above 25, the factor is applied to
 *    `ref + 0.4 × (weight − ref)`, where `ref` is the weight at BMI 25 for that height. This follows the clinical
 *    practice of basing fluid and nutrition prescriptions on ideal or adjusted body weight for people with obesity
 *    (for example NICE CG174).
 * 3. **Upper reference cap.** The baseline never exceeds the 95th percentile of EFSA (2010) adult total water
 *    intakes (female 3.1 L, male 4.0 L) × 0.8. EFSA's values assume moderate temperature and moderate activity
 *    (PAL 1.6); heat and exercise are added on top by the separate adjustments.
 */
object GoalPolicyV2 : GoalPolicy by GoalPolicyV1 {
    override val version: Int = 2

    override fun perKgFactor(ageYears: Int): Int = when {
        ageYears <= 30 -> 28
        ageYears <= 55 -> 26
        ageYears <= 65 -> 24
        else -> 22
    }

    const val REFERENCE_BMI: Double = 25.0
    const val ADJUSTED_WEIGHT_FRACTION: Double = 0.4

    override fun calculationWeightKg(weightKg: Double, heightCm: Double?): Double {
        if (heightCm == null || heightCm <= 0.0) return weightKg
        val heightM = heightCm / 100.0
        val reference = REFERENCE_BMI * heightM * heightM
        return if (weightKg <= reference) weightKg else reference + ADJUSTED_WEIGHT_FRACTION * (weightKg - reference)
    }

    override fun baseCapMl(gender: Gender): Int = when (gender) {
        Gender.FEMALE -> 2480
        Gender.MALE -> 3200
        Gender.NON_BINARY, Gender.UNSPECIFIED -> 2840
    }
}
