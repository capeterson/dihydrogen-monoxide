@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.core.domain.stats

import java.time.LocalDate

enum class AnomalyDirection { HIGH, LOW }

data class Anomaly(val date: LocalDate, val totalMl: Int, val medianMl: Double, val robustSdMl: Double, val direction: AnomalyDirection)

fun anomalies(days: List<DayStat>, windowDays: Int = 30, minSamples: Int = 7, threshold: Double = 2.5): List<Anomaly> = TODO("T3")
