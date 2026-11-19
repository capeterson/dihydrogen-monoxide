package casa.peterson.dhmo.feature.profile

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import casa.peterson.dhmo.core.domain.format.VolumeFormatter
import casa.peterson.dhmo.core.model.ActivityLevel
import casa.peterson.dhmo.core.model.Climate
import casa.peterson.dhmo.core.model.ComponentKind
import casa.peterson.dhmo.core.model.GoalComponent
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

private const val FORECAST_WARM_ML = 250
private const val FORECAST_HOT_ML = 500
private const val FORECAST_VERY_HOT_ML = 750
private const val NUMBER_FRACTION_DIGITS = 1

/** Formats the breakdown lines of TECHNICAL_PLAN §7.3 in the user's units, for example "Base 2,310 ml (33 ml × 70 kg)". */
@Suppress("TooManyFunctions") // One small formatter per kind of component; they belong together.
internal class BreakdownLabels(private val resources: Resources, val units: VolumeUnit, val locale: Locale) {
    fun volume(ml: Int): String = VolumeFormatter.format(ml, units, locale)

    /** A volume with its sign, for ingredients: "+250 ml". */
    fun signed(ml: Int): String = (if (ml > 0) "+" else "") + volume(ml)

    /** The one-line label for [component]. Unknown or missing detail values fall back to safe defaults rather than throwing. */
    fun label(component: GoalComponent): String {
        val amount = volume(component.ml)
        val detail = component.detail
        return when (component.kind) {
            ComponentKind.BASE_CHILD_TABLE ->
                string(R.string.feature_profile_component_base_child, amount, detail["ageBand"].orEmpty().replace('-', '–'))

            ComponentKind.BASE_WEIGHT -> if (detail.containsKey("capMl")) {
                string(R.string.feature_profile_component_base_cap, amount, volume(detail.int("weightBaseMl")))
            } else {
                string(R.string.feature_profile_component_base_weight, amount, volume(detail.int("factor")), number(detail["weightKg"]))
            }

            ComponentKind.BASE_GENDER_FLOOR ->
                string(R.string.feature_profile_component_base_floor, amount, volume(detail.int("weightBaseMl")))

            ComponentKind.PREGNANCY -> string(R.string.feature_profile_component_pregnancy, amount)

            ComponentKind.LACTATION -> string(R.string.feature_profile_component_lactation, amount)

            ComponentKind.ACTIVITY_LEVEL -> string(levelLabel(detail["level"]), amount)

            ComponentKind.ACTIVITY_MINUTES -> string(
                if (detail["capped"] == "true") {
                    R.string.feature_profile_component_minutes_capped
                } else {
                    R.string.feature_profile_component_minutes
                },
                amount,
                number(detail["minutes"]),
            )

            ComponentKind.CLIMATE_MANUAL -> string(climateLabel(detail["climate"]), amount)

            ComponentKind.CLIMATE_FORECAST -> string(forecastLabel(component.ml), amount, number(detail["maxApparentTempC"]))

            ComponentKind.ALTITUDE -> string(R.string.feature_profile_component_altitude, amount)
        }
    }

    /** A second line for [component], or null: the forecast fallback note of PLAN §4.4. */
    fun note(component: GoalComponent): String? =
        if (component.kind == ComponentKind.CLIMATE_MANUAL && component.detail["fallback"] == "true") {
            resources.getString(R.string.feature_profile_forecast_fallback)
        } else {
            null
        }

    private fun string(id: Int, vararg args: Any): String = resources.getString(id, *args)

    /** The working under a recipe row ("34 ml × 72 kg"), or null when the row says it all. */
    fun detail(component: GoalComponent): String? {
        val d = component.detail
        return when (component.kind) {
            ComponentKind.BASE_WEIGHT -> when {
                d.containsKey("capMl") -> string(R.string.feature_profile_detail_base_cap, volume(d.int("weightBaseMl")))

                d.containsKey("actualWeightKg") -> string(
                    R.string.feature_profile_detail_base_weight_adjusted,
                    volume(d.int("factor")),
                    number(d["weightKg"]),
                    number(d["actualWeightKg"]),
                )

                else -> string(R.string.feature_profile_detail_base_weight, volume(d.int("factor")), number(d["weightKg"]))
            }

            ComponentKind.ACTIVITY_MINUTES -> string(R.string.feature_profile_detail_minutes, number(d["minutes"]))

            ComponentKind.CLIMATE_FORECAST -> string(R.string.feature_profile_detail_forecast, number(d["maxApparentTempC"]))

            else -> null
        }
    }

    /** What the user gave the calculation: weight, active minutes, the forecast and the kind of base, as (name, value) pairs. */
    fun inputs(components: List<GoalComponent>): List<Pair<Int, String>> = components.filter { it.applied }.mapNotNull { c ->
        when (c.kind) {
            ComponentKind.BASE_WEIGHT ->
                R.string.feature_profile_ingredient_weight to
                    string(R.string.feature_profile_input_kg, number(c.detail["actualWeightKg"] ?: c.detail["weightKg"]))

            ComponentKind.BASE_GENDER_FLOOR -> R.string.feature_profile_ingredient_base to string(R.string.feature_profile_input_adult)

            ComponentKind.BASE_CHILD_TABLE -> R.string.feature_profile_ingredient_base to c.detail["ageBand"].orEmpty().replace('-', '–')

            ComponentKind.ACTIVITY_MINUTES ->
                R.string.feature_profile_ingredient_active to
                    string(R.string.feature_profile_input_minutes, number(c.detail["minutes"]))

            ComponentKind.CLIMATE_FORECAST ->
                R.string.feature_profile_ingredient_forecast to
                    string(R.string.feature_profile_input_celsius, number(c.detail["maxApparentTempC"]))

            else -> null
        }
    }

    /** A `Locale.ROOT` decimal from a `detail` map, reformatted for [locale]. */
    private fun number(raw: String?): String {
        val value = raw?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val format = NumberFormat.getNumberInstance(locale)
        format.maximumFractionDigits = NUMBER_FRACTION_DIGITS
        return format.format(value)
    }

    private fun Map<String, String>.int(key: String): Int = this[key]?.toIntOrNull() ?: 0

    private fun levelLabel(level: String?): Int = when (level?.let { name -> ActivityLevel.entries.find { it.name == name } }) {
        ActivityLevel.LIGHT -> R.string.feature_profile_component_level_light
        ActivityLevel.MODERATE -> R.string.feature_profile_component_level_moderate
        ActivityLevel.VERY_ACTIVE -> R.string.feature_profile_component_level_very_active
        ActivityLevel.SEDENTARY, null -> R.string.feature_profile_component_level_sedentary
    }

    private fun climateLabel(climate: String?): Int = when (climate?.let { name -> Climate.entries.find { it.name == name } }) {
        Climate.WARM -> R.string.feature_profile_component_climate_warm
        Climate.HOT_HUMID -> R.string.feature_profile_component_climate_hot_humid
        Climate.TEMPERATE, null -> R.string.feature_profile_component_climate_temperate
    }

    private fun forecastLabel(ml: Int): Int = when {
        ml >= FORECAST_VERY_HOT_ML -> R.string.feature_profile_component_forecast_very_hot
        ml >= FORECAST_HOT_ML -> R.string.feature_profile_component_forecast_hot
        ml >= FORECAST_WARM_ML -> R.string.feature_profile_component_forecast_warm
        else -> R.string.feature_profile_component_forecast_mild
    }
}

@Composable
internal fun breakdownLabels(units: VolumeUnit): BreakdownLabels =
    BreakdownLabels(LocalResources.current, units, LocalConfiguration.current.locales[0])
