@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.core.domain.stats

/**
 * Trailing mean over the window ending at each index. Element i is null when `i < window - 1`, or when the
 * window holds no included day.
 */
fun rollingMean(days: List<DayStat>, window: Int, includeNoLogDays: Boolean): List<Double?> = TODO("T3")
