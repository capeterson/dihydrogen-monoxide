package casa.peterson.dhmo.core.domain.goal

import casa.peterson.dhmo.core.model.ActivityInput
import casa.peterson.dhmo.core.model.ClimateInput
import casa.peterson.dhmo.core.model.ComponentKind
import casa.peterson.dhmo.core.model.GoalBreakdown
import casa.peterson.dhmo.core.model.GoalComponent
import casa.peterson.dhmo.core.model.GoalInput
import casa.peterson.dhmo.core.model.PregnancyStatus
import java.math.BigDecimal

class GoalCalculator(private val policy: GoalPolicy = GoalPolicyV1) {
    fun calculate(input: GoalInput): GoalBreakdown {
        val profile = input.profile
        val age = GoalMath.ageYears(profile.birthDate, input.date)
        val child = age < policy.adultAgeYears

        val components = buildList {
            add(baseComponent(age, child, input))
            if (profile.pregnancyStatus == PregnancyStatus.PREGNANT) {
                add(GoalComponent(ComponentKind.PREGNANCY, policy.pregnancyMl, input.toggles.pregnancyLactation))
            }
            if (profile.lactating) {
                add(GoalComponent(ComponentKind.LACTATION, policy.lactationMl, input.toggles.pregnancyLactation))
            }
            add(activityComponent(input))
            add(climateComponent(input))
            if (profile.highAltitude) {
                add(GoalComponent(ComponentKind.ALTITUDE, policy.altitudeMl, input.toggles.altitude))
            }
        }

        val raw = components.filter { it.applied }.sumOf { it.ml }
        val min = if (child) policy.childMinMl else policy.adultMinMl
        val max = if (child) policy.childMaxMl else policy.adultMaxMl
        val (clamped, rounded, flag) = GoalMath.clampAndRound(raw, min, max)
        return GoalBreakdown(policy.version, age, components, raw, clamped, rounded, flag)
    }

    private fun baseComponent(age: Int, child: Boolean, input: GoalInput): GoalComponent {
        val profile = input.profile
        if (child) {
            return GoalComponent(
                ComponentKind.BASE_CHILD_TABLE,
                policy.childBaseMl(age, profile.gender),
                applied = true,
                detail = mapOf("ageBand" to policy.childAgeBand(age)),
            )
        }
        val factor = policy.perKgFactor(age)
        val weightBase = GoalMath.weightBaseMl(profile.weightKg, factor)
        val floor = policy.genderFloorMl(profile.gender)
        val weight = num(profile.weightKg)
        return if (weightBase < floor) {
            GoalComponent(
                ComponentKind.BASE_GENDER_FLOOR,
                floor,
                applied = true,
                detail = mapOf(
                    "weightKg" to weight,
                    "factor" to factor.toString(),
                    "weightBaseMl" to weightBase.toString(),
                    "floorMl" to floor.toString(),
                ),
            )
        } else {
            GoalComponent(
                ComponentKind.BASE_WEIGHT,
                weightBase,
                applied = true,
                detail = mapOf("weightKg" to weight, "factor" to factor.toString()),
            )
        }
    }

    private fun activityComponent(input: GoalInput): GoalComponent {
        val applied = input.toggles.activity
        return when (val activity = input.activity) {
            is ActivityInput.Level -> GoalComponent(
                ComponentKind.ACTIVITY_LEVEL,
                policy.activityLevelMl(activity.level),
                applied,
                mapOf("level" to activity.level.name),
            )

            is ActivityInput.Sessions -> {
                val uncapped = activity.sessions.sumOf { it.minutes * policy.mlPerMinute(it.intensity) }
                GoalComponent(
                    ComponentKind.ACTIVITY_MINUTES,
                    minOf(uncapped, policy.activityMinutesCapMl),
                    applied,
                    mapOf(
                        "minutes" to activity.sessions.sumOf { it.minutes }.toString(),
                        "capped" to (uncapped > policy.activityMinutesCapMl).toString(),
                    ),
                )
            }
        }
    }

    private fun climateComponent(input: GoalInput): GoalComponent {
        val applied = input.toggles.climate
        return when (val climate = input.climate) {
            is ClimateInput.Manual -> GoalComponent(
                ComponentKind.CLIMATE_MANUAL,
                policy.climateManualMl(climate.climate),
                applied,
                buildMap {
                    put("climate", climate.climate.name)
                    if (climate.forecastUnavailable) put("fallback", "true")
                },
            )

            is ClimateInput.Forecast -> GoalComponent(
                ComponentKind.CLIMATE_FORECAST,
                policy.climateForecastMl(climate.maxApparentTempC),
                applied,
                mapOf("maxApparentTempC" to num(climate.maxApparentTempC)),
            )
        }
    }

    /** Locale-independent plain decimal without trailing zeros (70.0 -> "70", 48.5 -> "48.5"). */
    private fun num(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}
