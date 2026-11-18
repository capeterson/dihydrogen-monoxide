@file:Suppress("MagicNumber") // this file is the constants table of TECHNICAL_PLAN §7.2

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

    fun perKgFactor(ageYears: Int): Int

    fun genderFloorMl(gender: Gender): Int

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
