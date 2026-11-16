package casa.peterson.dhmo.core.domain.stats

import java.time.LocalDate

/** Default comparison tolerance of the golden tables (TECHNICAL_PLAN §9.1). */
const val TOLERANCE = 1e-3

const val GOAL_ML = 2000

val MONDAY_MARCH_2: LocalDate = LocalDate.of(2026, 3, 2)

/** One [DayStat] per total, from [start]; a total of 0 is a day without entries, otherwise 3 entries. */
fun daysOf(totals: List<Int>, start: LocalDate = MONDAY_MARCH_2, goal: Int? = GOAL_ML): List<DayStat> =
    totals.mapIndexed { index, total -> DayStat(start.plusDays(index.toLong()), total, goal, if (total == 0) 0 else 3) }

/** Dataset A of TECHNICAL_PLAN §9.14: Monday 2026-03-02 to Sunday 2026-03-15, Wednesday 04 without a log. */
val DATASET_A_TOTALS = listOf(2100, 1800, 0, 2500, 1950, 2000, 1200, 2200, 2300, 1600, 2000, 2050, 900, 2400)

fun datasetA(): List<DayStat> = daysOf(DATASET_A_TOTALS)

/** The 13 logged totals of dataset A. */
val DATASET_A_LOGGED: List<Double> = DATASET_A_TOTALS.filter { it != 0 }.map { it.toDouble() }

val DATASET_B = listOf(1500, 1600, 1550, 1700, 1800, 1750, 1900, 2000, 1950, 2100)
val DATASET_C = listOf(2000, 2000, 1500, 2500, 2000, 1500, 2500, 2000, 2000, 2000)
val DATASET_D = listOf(2400, 2300, 2350, 2200, 2100, 2150, 2000, 1900, 1950, 1800, 1700, 1750)
val DATASET_E = listOf(1500, 1600, 1550, 1700, 5000, 1750, 1900, 2000, 1950, 2100)
val DATASET_F = listOf(2000, 2100, 1900, 2050, 1950, 2000, 2200, 1800, 5000, 2000, 500, 2050)

fun List<Int>.asDoubles(): List<Double> = map { it.toDouble() }

/** Dataset G of TECHNICAL_PLAN §9.14: (max apparent temperature, total ml). */
val DATASET_G: List<Pair<Double, Double>> = listOf(
    18 to 1800, 22 to 2000, 25 to 2100, 30 to 2600, 28 to 2300, 15 to 1700, 33 to 2900,
    21 to 2000, 27 to 2500, 24 to 2100, 19 to 1900, 31 to 2700, 35 to 2600, 26 to 2200,
).map { (temp, ml) -> temp.toDouble() to ml.toDouble() }
