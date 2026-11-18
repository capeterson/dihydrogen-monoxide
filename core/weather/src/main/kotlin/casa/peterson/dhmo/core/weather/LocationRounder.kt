package casa.peterson.dhmo.core.weather

import kotlin.math.roundToInt

/** Coordinates leave the device only after rounding to 0.1 degrees (about 11 km). */
internal object LocationRounder {
    fun toE1(degrees: Double): Int = (degrees * 10.0).roundToInt()
}
