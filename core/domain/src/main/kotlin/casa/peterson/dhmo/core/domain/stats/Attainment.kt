package casa.peterson.dhmo.core.domain.stats

private const val PERCENT = 100L
private const val HALF = 50L
private const val FOUR_FIFTHS = 80L

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

fun attainment(days: List<DayStat>, includeNoLogDays: Boolean): Attainment {
    val withGoal = days.included(includeNoLogDays).filter { (it.goalMl ?: 0) > 0 }
    var below50 = 0
    var from50To80 = 0
    var from80To100 = 0
    var atLeast100 = 0
    for (day in withGoal) {
        val scaledTotal = day.totalMl * PERCENT
        val goal = (day.goalMl ?: 0).toLong()
        when {
            day.totalMl >= goal -> atLeast100++
            scaledTotal >= goal * FOUR_FIFTHS -> from80To100++
            scaledTotal >= goal * HALF -> from50To80++
            else -> below50++
        }
    }
    return Attainment(
        dayCount = withGoal.size,
        daysMet = atLeast100,
        rate = if (withGoal.isEmpty()) 0.0 else atLeast100.toDouble() / withGoal.size,
        below50 = below50,
        from50To80 = from50To80,
        from80To100 = from80To100,
        atLeast100 = atLeast100,
    )
}

/** Chart colour classes (PLAN §7.1). */
enum class DayResult { MET, PARTLY_MET, MISSED, NO_DATA }

/** NO_DATA: not logged or no goal; MET: >= 100%; PARTLY_MET: >= 80%; else MISSED. */
fun dayResult(day: DayStat): DayResult {
    val goal = day.goalMl?.toLong()?.takeIf { it > 0 }
    return when {
        !day.logged || goal == null -> DayResult.NO_DATA
        day.totalMl >= goal -> DayResult.MET
        day.totalMl * PERCENT >= goal * FOUR_FIFTHS -> DayResult.PARTLY_MET
        else -> DayResult.MISSED
    }
}
