package casa.peterson.dhmo.feature.profile

import casa.peterson.dhmo.core.domain.format.VolumeFormatter
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import java.math.BigDecimal
import java.math.RoundingMode

/** Manual targets accepted by day and global overrides (TECHNICAL_PLAN §7.2). */
internal val MANUAL_TARGET_RANGE = 250..10_000

private const val LITRE_DIGITS = 2
private const val OUNCE_DIGITS = 1

/**
 * A text field holding a volume in the user's unit. [initialMl] is kept so that saving an untouched field returns the
 * stored value exactly, even when converting it to fluid ounces and back would move it by a millilitre.
 */
internal data class VolumeField(val text: String, val initialMl: Int?) {
    fun toMl(unit: VolumeUnit): Int? = if (initialMl != null && text == format(initialMl, unit)) {
        initialMl
    } else {
        text.trim().replace(',', '.').toBigDecimalOrNull()
            ?.takeIf { it.signum() >= 0 }
            ?.let { value -> runCatching { VolumeFormatter.toMl(value.toDouble(), unit) }.getOrNull() }
    }

    companion object {
        fun of(ml: Int?, unit: VolumeUnit): VolumeField = VolumeField(ml?.let { format(it, unit) }.orEmpty(), ml)

        /** Plain digits for editing: no grouping, trailing zeros trimmed, `.` as the decimal separator. */
        fun format(ml: Int, unit: VolumeUnit): String {
            val digits = when (unit) {
                VolumeUnit.ML -> 0
                VolumeUnit.L -> LITRE_DIGITS
                VolumeUnit.US_FL_OZ, VolumeUnit.IMP_FL_OZ -> OUNCE_DIGITS
            }
            return BigDecimal.valueOf(VolumeFormatter.fromMl(ml, unit)).setScale(digits, RoundingMode.HALF_UP).stripTrailingZeros()
                .toPlainString()
        }
    }
}
