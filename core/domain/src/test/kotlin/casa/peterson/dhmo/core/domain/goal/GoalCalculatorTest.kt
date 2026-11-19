package casa.peterson.dhmo.core.domain.goal

import casa.peterson.dhmo.core.model.ActivityInput
import casa.peterson.dhmo.core.model.ActivityLevel
import casa.peterson.dhmo.core.model.ClampFlag
import casa.peterson.dhmo.core.model.Climate
import casa.peterson.dhmo.core.model.ClimateInput
import casa.peterson.dhmo.core.model.ComponentKind
import casa.peterson.dhmo.core.model.ComponentKind.ACTIVITY_LEVEL
import casa.peterson.dhmo.core.model.ComponentKind.ACTIVITY_MINUTES
import casa.peterson.dhmo.core.model.ComponentKind.ALTITUDE
import casa.peterson.dhmo.core.model.ComponentKind.BASE_CHILD_TABLE
import casa.peterson.dhmo.core.model.ComponentKind.BASE_GENDER_FLOOR
import casa.peterson.dhmo.core.model.ComponentKind.BASE_WEIGHT
import casa.peterson.dhmo.core.model.ComponentKind.CLIMATE_FORECAST
import casa.peterson.dhmo.core.model.ComponentKind.CLIMATE_MANUAL
import casa.peterson.dhmo.core.model.ComponentKind.LACTATION
import casa.peterson.dhmo.core.model.ComponentKind.PREGNANCY
import casa.peterson.dhmo.core.model.ExerciseSession
import casa.peterson.dhmo.core.model.Gender
import casa.peterson.dhmo.core.model.GoalInput
import casa.peterson.dhmo.core.model.Intensity
import casa.peterson.dhmo.core.model.PregnancyStatus
import casa.peterson.dhmo.core.model.Profile
import casa.peterson.dhmo.core.model.settings.AdjustmentToggles
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.LocalDate
import org.junit.Test

/** Worked examples G01-G38 of TECHNICAL_PLAN §7.5. */
class GoalCalculatorTest {
    private val date = LocalDate.of(2026, 6, 10)
    private val calculator = GoalCalculator(GoalPolicyV1)

    private data class Adj(val kind: ComponentKind, val ml: Int, val applied: Boolean = true)

    private data class Row(
        val id: String,
        val age: Int,
        val gender: Gender,
        val kg: Double,
        val activity: ActivityInput = ActivityInput.Level(ActivityLevel.SEDENTARY),
        val climate: ClimateInput = ClimateInput.Manual(Climate.TEMPERATE),
        val pregnant: Boolean = false,
        val lactating: Boolean = false,
        val altitude: Boolean = false,
        val toggles: AdjustmentToggles = AdjustmentToggles(),
        val baseKind: ComponentKind,
        val baseMl: Int,
        val adjustments: List<Adj> = emptyList(),
        val raw: Int,
        val clamped: Int,
        val goal: Int,
        val clamp: ClampFlag = ClampFlag.NONE,
    )

    private fun level(l: ActivityLevel) = ActivityInput.Level(l)

    private fun sessions(vararg s: Pair<Int, Intensity>) = ActivityInput.Sessions(s.map { ExerciseSession(it.first, it.second) })

    private fun manual(c: Climate) = ClimateInput.Manual(c)

    private fun forecast(t: Double) = ClimateInput.Forecast(t)

    private val rows = listOf(
        Row(
            "G01", 13, Gender.FEMALE, 45.0, baseKind = BASE_CHILD_TABLE, baseMl = 1520, raw = 1520, clamped = 1520, goal = 1500,
            adjustments = listOf(Adj(ACTIVITY_LEVEL, 0), Adj(CLIMATE_MANUAL, 0)),
        ),
        Row(
            "G02", 13, Gender.MALE, 45.0, baseKind = BASE_CHILD_TABLE, baseMl = 1680, raw = 1680, clamped = 1680, goal = 1700,
            adjustments = listOf(Adj(ACTIVITY_LEVEL, 0), Adj(CLIMATE_MANUAL, 0)),
        ),
        Row(
            "G03", 14, Gender.FEMALE, 45.0, baseKind = BASE_GENDER_FLOOR, baseMl = 1600, raw = 1600, clamped = 1600, goal = 1600,
            adjustments = listOf(Adj(ACTIVITY_LEVEL, 0), Adj(CLIMATE_MANUAL, 0)),
        ),
        Row(
            "G04", 14, Gender.MALE, 50.0, baseKind = BASE_GENDER_FLOOR, baseMl = 2000, raw = 2000, clamped = 2000, goal = 2000,
            adjustments = listOf(Adj(ACTIVITY_LEVEL, 0), Adj(CLIMATE_MANUAL, 0)),
        ),
        Row(
            "G05", 30, Gender.MALE, 70.0, baseKind = BASE_WEIGHT, baseMl = 2450, raw = 2450, clamped = 2450, goal = 2450,
            adjustments = listOf(Adj(ACTIVITY_LEVEL, 0), Adj(CLIMATE_MANUAL, 0)),
        ),
        Row(
            "G06", 31, Gender.MALE, 70.0, baseKind = BASE_WEIGHT, baseMl = 2310, raw = 2310, clamped = 2310, goal = 2300,
            adjustments = listOf(Adj(ACTIVITY_LEVEL, 0), Adj(CLIMATE_MANUAL, 0)),
        ),
        Row(
            "G07", 55, Gender.FEMALE, 60.0, baseKind = BASE_WEIGHT, baseMl = 1980, raw = 1980, clamped = 1980, goal = 2000,
            adjustments = listOf(Adj(ACTIVITY_LEVEL, 0), Adj(CLIMATE_MANUAL, 0)),
        ),
        Row(
            "G08", 56, Gender.FEMALE, 60.0, baseKind = BASE_WEIGHT, baseMl = 1800, raw = 1800, clamped = 1800, goal = 1800,
            adjustments = listOf(Adj(ACTIVITY_LEVEL, 0), Adj(CLIMATE_MANUAL, 0)),
        ),
        Row(
            "G09", 65, Gender.MALE, 80.0, baseKind = BASE_WEIGHT, baseMl = 2400, raw = 2400, clamped = 2400, goal = 2400,
            adjustments = listOf(Adj(ACTIVITY_LEVEL, 0), Adj(CLIMATE_MANUAL, 0)),
        ),
        Row(
            "G10", 66, Gender.MALE, 80.0, baseKind = BASE_WEIGHT, baseMl = 2240, raw = 2240, clamped = 2240, goal = 2250,
            adjustments = listOf(Adj(ACTIVITY_LEVEL, 0), Adj(CLIMATE_MANUAL, 0)),
        ),
        Row(
            "G11", 40, Gender.FEMALE, 48.0, baseKind = BASE_GENDER_FLOOR, baseMl = 1600, raw = 1600, clamped = 1600, goal = 1600,
            adjustments = listOf(Adj(ACTIVITY_LEVEL, 0), Adj(CLIMATE_MANUAL, 0)),
        ),
        Row(
            "G12", 40, Gender.FEMALE, 48.5, baseKind = BASE_WEIGHT, baseMl = 1601, raw = 1601, clamped = 1601, goal = 1600,
            adjustments = listOf(Adj(ACTIVITY_LEVEL, 0), Adj(CLIMATE_MANUAL, 0)),
        ),
        Row(
            "G13", 25, Gender.UNSPECIFIED, 50.0, baseKind = BASE_GENDER_FLOOR, baseMl = 1800, raw = 1800, clamped = 1800, goal = 1800,
            adjustments = listOf(Adj(ACTIVITY_LEVEL, 0), Adj(CLIMATE_MANUAL, 0)),
        ),
        Row(
            "G14", 28, Gender.FEMALE, 65.0, pregnant = true, baseKind = BASE_WEIGHT, baseMl = 2275, raw = 2575, clamped = 2575,
            goal = 2600, adjustments = listOf(Adj(PREGNANCY, 300), Adj(ACTIVITY_LEVEL, 0), Adj(CLIMATE_MANUAL, 0)),
        ),
        Row(
            "G15", 32, Gender.FEMALE, 65.0, lactating = true, activity = level(ActivityLevel.LIGHT), baseKind = BASE_WEIGHT,
            baseMl = 2145, raw = 3095, clamped = 3095, goal = 3100,
            adjustments = listOf(Adj(LACTATION, 700), Adj(ACTIVITY_LEVEL, 250), Adj(CLIMATE_MANUAL, 0)),
        ),
        Row(
            "G16", 35, Gender.MALE, 82.4, activity = level(ActivityLevel.MODERATE), climate = manual(Climate.WARM),
            baseKind = BASE_WEIGHT, baseMl = 2719, raw = 3469, clamped = 3469, goal = 3450,
            adjustments = listOf(Adj(ACTIVITY_LEVEL, 500), Adj(CLIMATE_MANUAL, 250)),
        ),
        Row(
            "G17", 35, Gender.MALE, 82.4, activity = sessions(30 to Intensity.MODERATE), climate = manual(Climate.WARM),
            baseKind = BASE_WEIGHT, baseMl = 2719, raw = 3329, clamped = 3329, goal = 3350,
            adjustments = listOf(Adj(ACTIVITY_MINUTES, 360), Adj(CLIMATE_MANUAL, 250)),
        ),
        Row(
            "G18", 35, Gender.MALE, 82.4, activity = sessions(45 to Intensity.VIGOROUS, 20 to Intensity.LIGHT),
            baseKind = BASE_WEIGHT, baseMl = 2719, raw = 3649, clamped = 3649, goal = 3650,
            adjustments = listOf(Adj(ACTIVITY_MINUTES, 930), Adj(CLIMATE_MANUAL, 0)),
        ),
        Row(
            "G19", 35, Gender.MALE, 82.4, activity = sessions(), baseKind = BASE_WEIGHT, baseMl = 2719, raw = 2719,
            clamped = 2719, goal = 2700, adjustments = listOf(Adj(ACTIVITY_MINUTES, 0), Adj(CLIMATE_MANUAL, 0)),
        ),
        Row(
            "G20", 45, Gender.FEMALE, 70.0, climate = forecast(24.9), baseKind = BASE_WEIGHT, baseMl = 2310, raw = 2310,
            clamped = 2310, goal = 2300, adjustments = listOf(Adj(ACTIVITY_LEVEL, 0), Adj(CLIMATE_FORECAST, 0)),
        ),
        Row(
            "G21", 45, Gender.FEMALE, 70.0, climate = forecast(25.0), baseKind = BASE_WEIGHT, baseMl = 2310, raw = 2560,
            clamped = 2560, goal = 2550, adjustments = listOf(Adj(ACTIVITY_LEVEL, 0), Adj(CLIMATE_FORECAST, 250)),
        ),
        Row(
            "G22", 45, Gender.FEMALE, 70.0, climate = forecast(29.9), baseKind = BASE_WEIGHT, baseMl = 2310, raw = 2560,
            clamped = 2560, goal = 2550, adjustments = listOf(Adj(ACTIVITY_LEVEL, 0), Adj(CLIMATE_FORECAST, 250)),
        ),
        Row(
            "G23", 45, Gender.FEMALE, 70.0, climate = forecast(30.0), baseKind = BASE_WEIGHT, baseMl = 2310, raw = 2810,
            clamped = 2810, goal = 2800, adjustments = listOf(Adj(ACTIVITY_LEVEL, 0), Adj(CLIMATE_FORECAST, 500)),
        ),
        Row(
            "G24", 45, Gender.FEMALE, 70.0, climate = forecast(34.9), baseKind = BASE_WEIGHT, baseMl = 2310, raw = 2810,
            clamped = 2810, goal = 2800, adjustments = listOf(Adj(ACTIVITY_LEVEL, 0), Adj(CLIMATE_FORECAST, 500)),
        ),
        Row(
            "G25", 45, Gender.FEMALE, 70.0, climate = forecast(35.0), baseKind = BASE_WEIGHT, baseMl = 2310, raw = 3060,
            clamped = 3060, goal = 3050, adjustments = listOf(Adj(ACTIVITY_LEVEL, 0), Adj(CLIMATE_FORECAST, 750)),
        ),
        Row(
            "G26", 22, Gender.MALE, 150.0, activity = sessions(200 to Intensity.VIGOROUS), climate = forecast(36.0),
            altitude = true, baseKind = BASE_WEIGHT, baseMl = 5250, raw = 8250, clamped = 6000, goal = 6000,
            clamp = ClampFlag.HIGH,
            adjustments = listOf(Adj(ACTIVITY_MINUTES, 2000), Adj(CLIMATE_FORECAST, 750), Adj(ALTITUDE, 250)),
        ),
        Row(
            "G27", 22, Gender.MALE, 160.0, activity = level(ActivityLevel.VERY_ACTIVE), climate = forecast(36.0),
            altitude = true, baseKind = BASE_WEIGHT, baseMl = 5600, raw = 7350, clamped = 6000, goal = 6000,
            clamp = ClampFlag.HIGH,
            adjustments = listOf(Adj(ACTIVITY_LEVEL, 750), Adj(CLIMATE_FORECAST, 750), Adj(ALTITUDE, 250)),
        ),
        Row(
            "G28", 1, Gender.FEMALE, 10.0, baseKind = BASE_CHILD_TABLE, baseMl = 920, raw = 920, clamped = 920, goal = 900,
            adjustments = listOf(Adj(ACTIVITY_LEVEL, 0), Adj(CLIMATE_MANUAL, 0)),
        ),
        Row(
            "G29", 0, Gender.MALE, 8.0, baseKind = BASE_CHILD_TABLE, baseMl = 720, raw = 720, clamped = 720, goal = 700,
            adjustments = listOf(Adj(ACTIVITY_LEVEL, 0), Adj(CLIMATE_MANUAL, 0)),
        ),
        Row(
            "G30", 8, Gender.MALE, 30.0, activity = level(ActivityLevel.VERY_ACTIVE), climate = forecast(36.0),
            altitude = true, baseKind = BASE_CHILD_TABLE, baseMl = 1280, raw = 3030, clamped = 3000, goal = 3000,
            clamp = ClampFlag.HIGH,
            adjustments = listOf(Adj(ACTIVITY_LEVEL, 750), Adj(CLIMATE_FORECAST, 750), Adj(ALTITUDE, 250)),
        ),
        Row(
            "G31", 12, Gender.MALE, 40.0, activity = sessions(120 to Intensity.VIGOROUS), climate = forecast(36.0),
            altitude = true, baseKind = BASE_CHILD_TABLE, baseMl = 1680, raw = 4680, clamped = 3000, goal = 3000,
            clamp = ClampFlag.HIGH,
            adjustments = listOf(Adj(ACTIVITY_MINUTES, 2000), Adj(CLIMATE_FORECAST, 750), Adj(ALTITUDE, 250)),
        ),
        Row(
            "G32", 70, Gender.FEMALE, 58.3, activity = level(ActivityLevel.LIGHT), altitude = true, baseKind = BASE_WEIGHT,
            baseMl = 1632, raw = 2132, clamped = 2132, goal = 2150,
            adjustments = listOf(Adj(ACTIVITY_LEVEL, 250), Adj(CLIMATE_MANUAL, 0), Adj(ALTITUDE, 250)),
        ),
        Row(
            "G33", 29, Gender.FEMALE, 62.0, activity = level(ActivityLevel.MODERATE), climate = manual(Climate.HOT_HUMID),
            pregnant = true, lactating = true,
            toggles = AdjustmentToggles(activity = false, pregnancyLactation = false),
            baseKind = BASE_WEIGHT, baseMl = 2170, raw = 2670, clamped = 2670, goal = 2650,
            adjustments = listOf(
                Adj(PREGNANCY, 300, applied = false),
                Adj(LACTATION, 700, applied = false),
                Adj(ACTIVITY_LEVEL, 500, applied = false),
                Adj(CLIMATE_MANUAL, 500),
            ),
        ),
        Row(
            "G34", 50, Gender.MALE, 75.5, activity = level(ActivityLevel.LIGHT), climate = forecast(31.2),
            toggles = AdjustmentToggles(climate = false), baseKind = BASE_WEIGHT, baseMl = 2492, raw = 2742,
            clamped = 2742, goal = 2750,
            adjustments = listOf(Adj(ACTIVITY_LEVEL, 250), Adj(CLIMATE_FORECAST, 500, applied = false)),
        ),
        Row(
            "G35", 50, Gender.MALE, 75.5, activity = level(ActivityLevel.LIGHT), climate = manual(Climate.WARM),
            baseKind = BASE_WEIGHT, baseMl = 2492, raw = 2992, clamped = 2992, goal = 3000,
            adjustments = listOf(Adj(ACTIVITY_LEVEL, 250), Adj(CLIMATE_MANUAL, 250)),
        ),
        Row(
            "G36", 31, Gender.FEMALE, 70.7, baseKind = BASE_WEIGHT, baseMl = 2333, raw = 2333, clamped = 2333, goal = 2350,
            adjustments = listOf(Adj(ACTIVITY_LEVEL, 0), Adj(CLIMATE_MANUAL, 0)),
        ),
        Row(
            "G37", 20, Gender.FEMALE, 61.5, baseKind = BASE_WEIGHT, baseMl = 2153, raw = 2153, clamped = 2153, goal = 2150,
            adjustments = listOf(Adj(ACTIVITY_LEVEL, 0), Adj(CLIMATE_MANUAL, 0)),
        ),
        Row(
            "G38", 60, Gender.MALE, 67.5, baseKind = BASE_WEIGHT, baseMl = 2025, raw = 2025, clamped = 2025, goal = 2050,
            adjustments = listOf(Adj(ACTIVITY_LEVEL, 0), Adj(CLIMATE_MANUAL, 0)),
        ),
    )

    private fun inputOf(row: Row) = GoalInput(
        date = date,
        profile = Profile(
            birthDate = date.minusYears(row.age.toLong()),
            gender = row.gender,
            weightKg = row.kg,
            heightCm = null,
            activityLevel = ActivityLevel.SEDENTARY,
            climate = Climate.TEMPERATE,
            pregnancyStatus = if (row.pregnant) PregnancyStatus.PREGNANT else PregnancyStatus.NONE,
            lactating = row.lactating,
            highAltitude = row.altitude,
        ),
        activity = row.activity,
        climate = row.climate,
        toggles = row.toggles,
    )

    @Test
    fun workedExamplesG01ToG38() {
        assertThat(rows).hasSize(38)
        for (row in rows) {
            val b = calculator.calculate(inputOf(row))
            val msg = row.id
            assertWithMessage("$msg age").that(b.ageYears).isEqualTo(row.age)
            assertWithMessage("$msg version").that(b.policyVersion).isEqualTo(1)
            assertWithMessage("$msg base kind").that(b.components.first().kind).isEqualTo(row.baseKind)
            assertWithMessage("$msg base ml").that(b.components.first().ml).isEqualTo(row.baseMl)
            val actual = b.components.drop(1).map { Adj(it.kind, it.ml, it.applied) }
            assertWithMessage("$msg adjustments").that(actual).isEqualTo(row.adjustments)
            assertWithMessage("$msg raw").that(b.rawMl).isEqualTo(row.raw)
            assertWithMessage("$msg clamped").that(b.clampedMl).isEqualTo(row.clamped)
            assertWithMessage("$msg clamp").that(b.clamp).isEqualTo(row.clamp)
            assertWithMessage("$msg goal").that(b.calculatedMl).isEqualTo(row.goal)
        }
    }

    private fun calc(row: Row) = calculator.calculate(inputOf(row))

    private fun row(id: String) = rows.first { it.id == id }

    @Test
    fun detailKeys() {
        assertThat(calc(row("G01")).components[0].detail).containsExactly("ageBand", "9-13")
        assertThat(calc(row("G28")).components[0].detail).containsExactly("ageBand", "1")
        assertThat(calc(row("G29")).components[0].detail).containsExactly("ageBand", "0")
        assertThat(calc(row("G30")).components[0].detail).containsExactly("ageBand", "4-8")
        assertThat(calc(row("G06")).components[0].detail).containsExactly("weightKg", "70", "factor", "33")
        assertThat(calc(row("G12")).components[0].detail).containsExactly("weightKg", "48.5", "factor", "33")
        assertThat(calc(row("G11")).components[0].detail)
            .containsExactly("weightKg", "48", "factor", "33", "weightBaseMl", "1584", "floorMl", "1600")
        assertThat(calc(row("G14")).components[1].detail).isEmpty()
        assertThat(calc(row("G16")).components[1].detail).containsExactly("level", "MODERATE")
        assertThat(calc(row("G16")).components[2].detail).containsExactly("climate", "WARM")
        assertThat(calc(row("G17")).components[1].detail).containsExactly("minutes", "30", "capped", "false")
        assertThat(calc(row("G26")).components[1].detail).containsExactly("minutes", "200", "capped", "true")
        assertThat(calc(row("G26")).components[2].detail).containsExactly("maxApparentTempC", "36")
        assertThat(calc(row("G34")).components[2].detail).containsExactly("maxApparentTempC", "31.2")
        assertThat(calc(row("G26")).components[3].detail).isEmpty()
    }

    @Test
    fun forecastFallbackFlag() {
        val input = inputOf(row("G06")).copy(climate = ClimateInput.Manual(Climate.WARM, forecastUnavailable = true))
        val climate = calculator.calculate(input).components.last()
        assertThat(climate.kind).isEqualTo(CLIMATE_MANUAL)
        assertThat(climate.detail).containsExactly("climate", "WARM", "fallback", "true")
    }

    @Test
    fun componentOrderIsFixed() {
        val row = row("G33").copy(altitude = true)
        val kinds = calc(row).components.map { it.kind }
        assertThat(kinds).containsExactly(BASE_WEIGHT, PREGNANCY, LACTATION, ACTIVITY_LEVEL, CLIMATE_MANUAL, ALTITUDE)
            .inOrder()
    }

    @Test
    fun altitudeToggleOffIsListedButNotApplied() {
        val row = row("G32").copy(toggles = AdjustmentToggles(altitude = false))
        val b = calc(row)
        assertThat(b.components.last()).isEqualTo(
            casa.peterson.dhmo.core.model.GoalComponent(ALTITUDE, 250, applied = false),
        )
        assertThat(b.rawMl).isEqualTo(1632 + 250)
    }
}
