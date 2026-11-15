package casa.peterson.dhmo.core.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * The hydration day an entry belongs to (TECHNICAL_PLAN §4.2): the local date of [instant] at [offset],
 * shifted back by [dayStart] so that a day starting at 04:00 owns the hours until 03:59 of the next date.
 */
fun hydrationDay(instant: Instant, offset: ZoneOffset, dayStart: LocalTime): LocalDate = instant
    .atOffset(offset)
    .toLocalDateTime()
    .minusSeconds(dayStart.toSecondOfDay().toLong())
    .toLocalDate()
