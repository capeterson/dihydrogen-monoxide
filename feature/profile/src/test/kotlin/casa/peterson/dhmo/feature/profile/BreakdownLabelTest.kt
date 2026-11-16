package casa.peterson.dhmo.feature.profile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.model.ComponentKind
import casa.peterson.dhmo.core.model.GoalComponent
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith

/** Every [ComponentKind] renders its TECHNICAL_PLAN §7.3 label, in millilitres and in US fluid ounces. */
@RunWith(AndroidJUnit4::class)
class BreakdownLabelTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources
    private val ml = BreakdownLabels(resources, VolumeUnit.ML, Locale.US)
    private val oz = BreakdownLabels(resources, VolumeUnit.US_FL_OZ, Locale.US)

    private data class Case(val component: GoalComponent, val ml: String, val oz: String)

    private val cases = listOf(
        Case(
            GoalComponent(ComponentKind.BASE_CHILD_TABLE, 1520, true, mapOf("ageBand" to "9-13")),
            "Base 1,520 ml (age 9–13)",
            "Base 51.4 fl oz (age 9–13)",
        ),
        Case(
            GoalComponent(ComponentKind.BASE_WEIGHT, 2310, true, mapOf("weightKg" to "70", "factor" to "33")),
            "Base 2,310 ml (33 ml × 70 kg)",
            "Base 78.1 fl oz (1.1 fl oz × 70 kg)",
        ),
        Case(
            GoalComponent(
                ComponentKind.BASE_SEX_FLOOR,
                1600,
                true,
                mapOf("weightKg" to "48", "factor" to "33", "weightBaseMl" to "1584", "floorMl" to "1600"),
            ),
            "Base 1,600 ml (minimum recommended intake; weight-based value was 1,584 ml)",
            "Base 54.1 fl oz (minimum recommended intake; weight-based value was 53.6 fl oz)",
        ),
        Case(GoalComponent(ComponentKind.PREGNANCY, 300, true), "+300 ml pregnancy", "+10.1 fl oz pregnancy"),
        Case(GoalComponent(ComponentKind.LACTATION, 700, true), "+700 ml breastfeeding", "+23.7 fl oz breastfeeding"),
        Case(
            GoalComponent(ComponentKind.ACTIVITY_LEVEL, 500, true, mapOf("level" to "MODERATE")),
            "+500 ml moderately active",
            "+16.9 fl oz moderately active",
        ),
        Case(
            GoalComponent(ComponentKind.ACTIVITY_MINUTES, 360, true, mapOf("minutes" to "30", "capped" to "false")),
            "+360 ml exercise (30 min)",
            "+12.2 fl oz exercise (30 min)",
        ),
        Case(
            GoalComponent(ComponentKind.CLIMATE_MANUAL, 250, true, mapOf("climate" to "WARM")),
            "+250 ml warm climate",
            "+8.5 fl oz warm climate",
        ),
        Case(
            GoalComponent(ComponentKind.CLIMATE_FORECAST, 500, true, mapOf("maxApparentTempC" to "32")),
            "+500 ml hot day (feels like 32 °C)",
            "+16.9 fl oz hot day (feels like 32 °C)",
        ),
        Case(GoalComponent(ComponentKind.ALTITUDE, 250, true), "+250 ml high altitude", "+8.5 fl oz high altitude"),
    )

    @Test
    fun everyKindIsCovered() {
        assertThat(cases.map { it.component.kind }.toSet()).containsExactlyElementsIn(ComponentKind.entries)
    }

    @Test
    fun labelsInMillilitres() {
        cases.forEach { assertThat(ml.label(it.component).nbsp()).isEqualTo(it.ml) }
    }

    @Test
    fun labelsInFluidOunces() {
        cases.forEach { assertThat(oz.label(it.component).nbsp()).isEqualTo(it.oz) }
    }

    @Test
    fun cappedMinutesAndActivityLevels() {
        val capped = GoalComponent(ComponentKind.ACTIVITY_MINUTES, 2000, true, mapOf("minutes" to "200", "capped" to "true"))
        assertThat(ml.label(capped).nbsp()).isEqualTo("+2,000 ml exercise (200 min, capped at the daily maximum)")
        val levels = mapOf("SEDENTARY" to "+0 ml sedentary", "LIGHT" to "+250 ml lightly active", "VERY_ACTIVE" to "+750 ml very active")
        levels.forEach { (level, expected) ->
            val amount = expected.substringAfter('+').substringBefore(' ').toInt()
            assertThat(ml.label(GoalComponent(ComponentKind.ACTIVITY_LEVEL, amount, true, mapOf("level" to level))).nbsp())
                .isEqualTo(expected)
        }
    }

    @Test
    fun climateAndForecastBands() {
        assertThat(ml.label(GoalComponent(ComponentKind.CLIMATE_MANUAL, 0, true, mapOf("climate" to "TEMPERATE"))).nbsp())
            .isEqualTo("+0 ml temperate climate")
        assertThat(ml.label(GoalComponent(ComponentKind.CLIMATE_MANUAL, 500, true, mapOf("climate" to "HOT_HUMID"))).nbsp())
            .isEqualTo("+500 ml hot or humid climate")
        val forecast = listOf(
            0 to "24.9" to "+0 ml mild day (feels like 24.9 °C)",
            250 to "25" to "+250 ml warm day (feels like 25 °C)",
            750 to "36" to "+750 ml very hot day (feels like 36 °C)",
        )
        forecast.forEach { (input, expected) ->
            val component = GoalComponent(ComponentKind.CLIMATE_FORECAST, input.first, true, mapOf("maxApparentTempC" to input.second))
            assertThat(ml.label(component).nbsp()).isEqualTo(expected)
        }
    }

    @Test
    fun fallbackNoteOnlyForMissingForecast() {
        val fallback = GoalComponent(ComponentKind.CLIMATE_MANUAL, 250, true, mapOf("climate" to "WARM", "fallback" to "true"))
        assertThat(ml.note(fallback)).isEqualTo("Forecast unavailable, using your climate setting")
        assertThat(ml.note(fallback.copy(detail = mapOf("climate" to "WARM")))).isNull()
        assertThat(ml.note(GoalComponent(ComponentKind.ALTITUDE, 250, true))).isNull()
    }

    @Test
    fun localeFormatsNumbers() {
        val german = BreakdownLabels(resources, VolumeUnit.ML, Locale.GERMANY)
        val component = GoalComponent(ComponentKind.BASE_WEIGHT, 2719, true, mapOf("weightKg" to "82.4", "factor" to "33"))
        assertThat(german.label(component).nbsp()).isEqualTo("Base 2.719 ml (33 ml × 82,4 kg)")
    }

    private fun String.nbsp() = replace(' ', ' ')
}
