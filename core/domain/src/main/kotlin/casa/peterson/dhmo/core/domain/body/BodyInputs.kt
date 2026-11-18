package casa.peterson.dhmo.core.domain.body

import casa.peterson.dhmo.core.model.settings.BodyUnits
import java.math.BigDecimal
import java.math.RoundingMode

/** Conversions between the units people type and the kilograms and centimetres that are stored. */
object BodyMeasures {
    private const val LB_PER_KG = 2.2046226218488
    private const val CM_PER_INCH = 2.54
    private const val INCHES_PER_FOOT = 12
    private const val TENTHS = 10
    private const val DECIMALS = 1

    fun kgToLb(kg: Double): Double = kg * LB_PER_KG

    fun lbToKg(lb: Double): Double = lb / LB_PER_KG

    fun feetInchesToCm(feet: Double, inches: Double): Double = (feet * INCHES_PER_FOOT + inches) * CM_PER_INCH

    /** Whole feet and inches to one decimal; 71.96 inches is 6 ft 0 in, not 5 ft 12 in. */
    fun cmToFeetInches(cm: Double): Pair<Int, Double> {
        val tenthsOfInch = Math.round(cm / CM_PER_INCH * TENTHS)
        val tenthsPerFoot = INCHES_PER_FOOT * TENTHS
        val feet = (tenthsOfInch / tenthsPerFoot).toInt()
        return feet to (tenthsOfInch - feet * tenthsPerFoot) / TENTHS.toDouble()
    }

    /** Accepts a decimal comma. Null for blank or unreadable text. */
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    /** One decimal at most, no trailing zeros, always a point: `70.0` is `70`, `154.32` is `154.3`. */
    fun plain(value: Double): String =
        BigDecimal.valueOf(value).setScale(DECIMALS, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}

/**
 * The weight and height fields as typed, in one unit system at a time. Switching systems converts what is already
 * typed, so nothing has to be entered again; text that cannot be read is left exactly as it is.
 */
data class BodyInputs(
    val units: BodyUnits = BodyUnits.METRIC,
    val weight: String = "",
    /** Used when [units] is metric. */
    val heightCm: String = "",
    /** Used when [units] is imperial. */
    val heightFeet: String = "",
    val heightInches: String = "",
) {
    fun switchTo(target: BodyUnits): BodyInputs {
        if (target == units) return this
        val newWeight = BodyMeasures.parse(weight)?.let {
            BodyMeasures.plain(if (target == BodyUnits.IMPERIAL) BodyMeasures.kgToLb(it) else BodyMeasures.lbToKg(it))
        } ?: weight
        return if (target == BodyUnits.IMPERIAL) {
            val (feet, inches) = BodyMeasures.parse(heightCm)?.let(BodyMeasures::cmToFeetInches) ?: (null to null)
            copy(
                units = target,
                weight = newWeight,
                heightFeet = feet?.toString() ?: "",
                heightInches = inches?.let(BodyMeasures::plain) ?: "",
            )
        } else {
            copy(units = target, weight = newWeight, heightCm = heightInFeetInchesToCm() ?: heightCm)
        }
    }

    /** The weight in kilograms, or null when it is blank or not a number. */
    fun weightKg(): Double? = BodyMeasures.parse(weight)?.let { if (units == BodyUnits.IMPERIAL) BodyMeasures.lbToKg(it) else it }

    /** True when no height was typed, in either unit system. */
    val heightBlank: Boolean
        get() = if (units == BodyUnits.IMPERIAL) heightFeet.isBlank() && heightInches.isBlank() else heightCm.isBlank()

    /** The height in centimetres, or null when blank or unreadable (check [heightBlank] to tell them apart). */
    fun heightCmValue(): Double? = if (units == BodyUnits.IMPERIAL) heightInFeetInchesToDouble() else BodyMeasures.parse(heightCm)

    private fun heightInFeetInchesToCm(): String? = heightInFeetInchesToDouble()?.let(BodyMeasures::plain)

    private fun heightInFeetInchesToDouble(): Double? {
        if (heightFeet.isBlank() && heightInches.isBlank()) return null
        val feet = if (heightFeet.isBlank()) 0.0 else BodyMeasures.parse(heightFeet) ?: return null
        val inches = if (heightInches.isBlank()) 0.0 else BodyMeasures.parse(heightInches) ?: return null
        return BodyMeasures.feetInchesToCm(feet, inches)
    }
}
