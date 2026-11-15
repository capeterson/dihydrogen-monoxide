@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.core.domain.stats

data class Summary(
    /** Included days. */
    val dayCount: Int,
    val totalMl: Long,
    val meanMl: Double,
    val medianMl: Double,
    val sdMl: Double,
    val minMl: Int,
    val maxMl: Int,
)

/** Null when no day is included. */
fun summary(days: List<DayStat>, includeNoLogDays: Boolean): Summary? = TODO("T3")
