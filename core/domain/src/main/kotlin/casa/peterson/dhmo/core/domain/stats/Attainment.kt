@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.core.domain.stats

data class Attainment(
    /** Included days that have a goal (`goalMl != null && goalMl > 0`). */
    val dayCount: Int,
    /** `totalMl >= goalMl`. */
    val daysMet: Int,
    /** `daysMet / dayCount`; 0.0 when `dayCount == 0`. */
    val rate: Double,
    val below50: Int,
    val from50To80: Int,
    val from80To100: Int,
    val atLeast100: Int,
)

fun attainment(days: List<DayStat>, includeNoLogDays: Boolean): Attainment = TODO("T3")

/** Chart colour classes (PLAN §7.1). */
enum class DayResult { MET, PARTLY_MET, MISSED, NO_DATA }

/** NO_DATA: not logged or no goal; MET: >= 100%; PARTLY_MET: >= 80%; else MISSED. */
fun dayResult(day: DayStat): DayResult = TODO("T3")
