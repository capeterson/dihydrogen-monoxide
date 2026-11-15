package casa.peterson.dhmo.core.domain.format

import casa.peterson.dhmo.core.model.settings.VolumeUnit
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Converts between millilitres (the storage unit) and the display units, and formats volumes for a locale.
 *
 * Volumes are stored as whole millilitres. Fluid ounces are converted with the US and imperial factors and
 * rounded half up to a whole millilitre when parsed back.
 */
object VolumeFormatter {
    const val ML_PER_LITRE: Double = 1000.0
    const val ML_PER_US_FL_OZ: Double = 29.5735
    const val ML_PER_IMP_FL_OZ: Double = 28.4131

    private const val LITRE_FRACTION_DIGITS = 2
    private const val OUNCE_FRACTION_DIGITS = 1

    /** The unit a first-run user gets: US fluid ounces for `en-US`, millilitres everywhere else. */
    fun defaultUnitFor(locale: Locale): VolumeUnit =
        if (locale.language == "en" && locale.country == "US") VolumeUnit.US_FL_OZ else VolumeUnit.ML

    /** Millilitres expressed in [unit] (unrounded). */
    fun fromMl(ml: Int, unit: VolumeUnit): Double = when (unit) {
        VolumeUnit.ML -> ml.toDouble()
        VolumeUnit.L -> ml / ML_PER_LITRE
        VolumeUnit.US_FL_OZ -> ml / ML_PER_US_FL_OZ
        VolumeUnit.IMP_FL_OZ -> ml / ML_PER_IMP_FL_OZ
    }

    /** [value] in [unit] converted to whole millilitres, halves rounding up. */
    fun toMl(value: Double, unit: VolumeUnit): Int {
        val ml =
            when (unit) {
                VolumeUnit.ML -> value
                VolumeUnit.L -> value * ML_PER_LITRE
                VolumeUnit.US_FL_OZ -> value * ML_PER_US_FL_OZ
                VolumeUnit.IMP_FL_OZ -> value * ML_PER_IMP_FL_OZ
            }
        return BigDecimal.valueOf(ml).setScale(0, RoundingMode.HALF_UP).intValueExact()
    }

    /** The short unit symbol: `ml`, `L` or `fl oz`. */
    fun symbol(unit: VolumeUnit): String = when (unit) {
        VolumeUnit.ML -> "ml"
        VolumeUnit.L -> "L"
        VolumeUnit.US_FL_OZ, VolumeUnit.IMP_FL_OZ -> "fl oz"
    }

    /** The number without a unit, grouped and with the decimal separator of [locale]: `1,250`, `1.25`, `8.5`. */
    fun formatNumber(ml: Int, unit: VolumeUnit, locale: Locale): String {
        val format = NumberFormat.getNumberInstance(locale)
        format.roundingMode = RoundingMode.HALF_UP
        format.minimumFractionDigits = 0
        format.maximumFractionDigits =
            when (unit) {
                VolumeUnit.ML -> 0
                VolumeUnit.L -> LITRE_FRACTION_DIGITS
                VolumeUnit.US_FL_OZ, VolumeUnit.IMP_FL_OZ -> OUNCE_FRACTION_DIGITS
            }
        return format.format(fromMl(ml, unit))
    }

    /** The number and the unit symbol, separated by a no-break space: `1,250 ml`, `1.25 L`, `8.5 fl oz`. */
    fun format(ml: Int, unit: VolumeUnit, locale: Locale): String = formatNumber(ml, unit, locale) + NO_BREAK_SPACE + symbol(unit)

    private const val NO_BREAK_SPACE = ' '
}
