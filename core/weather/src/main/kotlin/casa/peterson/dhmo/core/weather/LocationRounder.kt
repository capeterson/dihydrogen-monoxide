package casa.peterson.dhmo.core.weather

import kotlin.math.roundToInt

/** Coordinates leave the device only after rounding to 0.1 degrees (about 11 km). */
internal object LocationRounder {
    private const val TENTHS_PER_DEGREE = 10.0

    fun toE1(degrees: Double): Int = (degrees * TENTHS_PER_DEGREE).roundToInt()
}
