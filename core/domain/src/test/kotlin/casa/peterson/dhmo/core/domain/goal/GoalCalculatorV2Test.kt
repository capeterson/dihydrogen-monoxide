package casa.peterson.dhmo.core.domain.goal

import casa.peterson.dhmo.core.model.ActivityInput
import casa.peterson.dhmo.core.model.ActivityLevel
import casa.peterson.dhmo.core.model.ClampFlag
import casa.peterson.dhmo.core.model.Climate
import casa.peterson.dhmo.core.model.ClimateInput
import casa.peterson.dhmo.core.model.ComponentKind
import casa.peterson.dhmo.core.model.Gender
import casa.peterson.dhmo.core.model.GoalBreakdown
import casa.peterson.dhmo.core.model.GoalInput
import casa.peterson.dhmo.core.model.PregnancyStatus
import casa.peterson.dhmo.core.model.Profile
import casa.peterson.dhmo.core.model.settings.AdjustmentToggles
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/** Worked examples V01-V10 of TECHNICAL_PLAN §7.6. */
class GoalCalculatorV2Test {
    private val date = LocalDate.of(2026, 6, 10)

    private fun calc(
        age: Int,
        gender: Gender,
        kg: Double,
        heightCm: Double? = null,
        level: ActivityLevel = ActivityLevel.SEDENTARY,
        climate: Climate = Climate.TEMPERATE,
        policy: GoalPolicy = GoalPolicyV2,
    ): GoalBreakdown = GoalCalculator(policy).calculate(
        GoalInput(
            date = date,
            profile = Profile(
                birthDate = date.minusYears(age.toLong()),
                gender = gender,
                weightKg = kg,
                heightCm = heightCm,
                activityLevel = level,
                climate = climate,
                pregnancyStatus = PregnancyStatus.NONE,
                lactating = false,
                highAltitude = false,
            ),
            activity = ActivityInput.Level(level),
            climate = ClimateInput.Manual(climate),
            toggles = AdjustmentToggles(),
        ),
    )

    @Test
    fun `V01 large adult with height uses adjusted body weight`() {
        val b = calc(35, Gender.MALE, 120.0, heightCm = 185.0, level = ActivityLevel.MODERATE, climate = Climate.WARM)
        val base = b.components.first()
        assertThat(base.kind).isEqualTo(ComponentKind.BASE_WEIGHT)
        assertThat(base.ml).isEqualTo(2582) // 99.3 kg × 26
        assertThat(base.detail).containsExactly("weightKg", "99.3", "factor", "26", "actualWeightKg", "120")
        assertThat(b.rawMl).isEqualTo(3332)
        assertThat(b.calculatedMl).isEqualTo(3350)
        assertThat(b.policyVersion).isEqualTo(2)
    }

    @Test
    fun `V01 for comparison under v1 is 4,700 ml`() {
        val v1 = calc(35, Gender.MALE, 120.0, 185.0, ActivityLevel.MODERATE, Climate.WARM, policy = GoalPolicyV1)
        assertThat(v1.calculatedMl).isEqualTo(4700)
    }

    @Test
    fun `V02 large adult without height uses actual weight below the cap`() {
        val b = calc(35, Gender.MALE, 120.0, level = ActivityLevel.MODERATE, climate = Climate.WARM)
        assertThat(b.components.first().ml).isEqualTo(3120)
        assertThat(b.components.first().detail).doesNotContainKey("actualWeightKg")
        assertThat(b.rawMl).isEqualTo(3870)
        assertThat(b.calculatedMl).isEqualTo(3850)
    }

    @Test
    fun `V03 very large adult is capped at the upper reference intake`() {
        val base = calc(22, Gender.MALE, 160.0).components.first()
        assertThat(base.kind).isEqualTo(ComponentKind.BASE_WEIGHT)
        assertThat(base.ml).isEqualTo(3200)
        assertThat(base.detail).containsEntry("weightBaseMl", "4480")
        assertThat(base.detail).containsEntry("capMl", "3200")
    }

    @Test
    fun `V04 typical adult falls back to the EFSA drinks floor`() {
        val b = calc(30, Gender.MALE, 70.0, heightCm = 180.0)
        val base = b.components.first()
        assertThat(base.kind).isEqualTo(ComponentKind.BASE_GENDER_FLOOR)
        assertThat(base.ml).isEqualTo(2000)
        assertThat(base.detail).containsEntry("weightBaseMl", "1960")
        assertThat(base.detail).doesNotContainKey("actualWeightKg")
    }

    @Test
    fun `V05 slightly above BMI 25 is adjusted a little`() {
        val b = calc(31, Gender.MALE, 82.4, heightCm = 175.0)
        assertThat(b.components.first().detail).containsEntry("weightKg", "78.9")
        assertThat(b.components.first().ml).isEqualTo(2051)
        assertThat(b.calculatedMl).isEqualTo(2050)
    }

    @Test
    fun `V06 woman without height`() {
        assertThat(calc(45, Gender.FEMALE, 70.0).calculatedMl).isEqualTo(1800)
    }

    @Test
    fun `V07 female cap`() {
        val b = calc(28, Gender.FEMALE, 100.0)
        assertThat(b.components.first().ml).isEqualTo(2480)
        assertThat(b.calculatedMl).isEqualTo(2500)
    }

    @Test
    fun `V08 children are unchanged from v1`() {
        assertThat(calc(13, Gender.FEMALE, 45.0).calculatedMl).isEqualTo(1500)
    }

    @Test
    fun `V09 unspecified gender uses the mean cap`() {
        assertThat(calc(25, Gender.UNSPECIFIED, 110.0).components.first().ml).isEqualTo(2840)
    }

    @Test
    fun `V10 adjustments and clamps are inherited from v1`() {
        assertThat(GoalPolicyV2.activityLevelMl(ActivityLevel.VERY_ACTIVE)).isEqualTo(750)
        assertThat(GoalPolicyV2.adultMaxMl).isEqualTo(6000)
        assertThat(GoalPolicyV2.childBaseMl(10, Gender.MALE)).isEqualTo(1680)
        assertThat(calc(22, Gender.MALE, 160.0, level = ActivityLevel.VERY_ACTIVE).clamp).isEqualTo(ClampFlag.NONE)
    }

    @Test
    fun `calculation weight only changes above BMI 25 with a known height`() {
        assertThat(GoalPolicyV2.calculationWeightKg(120.0, null)).isEqualTo(120.0)
        assertThat(GoalPolicyV2.calculationWeightKg(70.0, 180.0)).isEqualTo(70.0)
        assertThat(GoalPolicyV2.calculationWeightKg(120.0, 185.0)).isWithin(1e-9).of(99.3375)
        assertThat(GoalPolicyV1.calculationWeightKg(120.0, 185.0)).isEqualTo(120.0)
        assertThat(GoalPolicyV1.baseCapMl(Gender.MALE)).isEqualTo(Int.MAX_VALUE)
    }

    @Test
    fun `current policy is v2`() {
        assertThat(CurrentGoalPolicy).isSameInstanceAs(GoalPolicyV2)
    }
}
