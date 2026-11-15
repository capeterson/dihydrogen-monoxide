package casa.peterson.dhmo.core.domain.pace

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** The real-time span of a hydration day in which the user is awake; `wake < sleep` (§8.1). */
data class WakeWindow(val wake: Instant, val sleep: Instant)

fun wakeWindow(day: LocalDate, wake: LocalTime, sleep: LocalTime, dayStart: LocalTime, zone: ZoneId): WakeWindow {
    val wakeLdt = if (wake >= dayStart) day.atTime(wake) else day.plusDays(1).atTime(wake)
    var sleepLdt = wakeLdt.toLocalDate().atTime(sleep)
    if (!sleepLdt.isAfter(wakeLdt)) sleepLdt = sleepLdt.plusDays(1)
    val dayEndLdt = day.plusDays(1).atTime(dayStart)
    if (sleepLdt.isAfter(dayEndLdt)) sleepLdt = dayEndLdt
    return WakeWindow(
        wake = ZonedDateTime.of(wakeLdt, zone).toInstant(),
        sleep = ZonedDateTime.of(sleepLdt, zone).toInstant(),
    )
}
