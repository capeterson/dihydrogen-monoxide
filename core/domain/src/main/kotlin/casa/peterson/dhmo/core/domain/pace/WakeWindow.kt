@file:Suppress("UNUSED_PARAMETER")

package casa.peterson.dhmo.core.domain.pace

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The real-time span of a hydration day in which the user is awake; `wake < sleep` (§8.1). */
data class WakeWindow(val wake: Instant, val sleep: Instant)

fun wakeWindow(day: LocalDate, wake: LocalTime, sleep: LocalTime, dayStart: LocalTime, zone: ZoneId): WakeWindow = TODO("T2")
