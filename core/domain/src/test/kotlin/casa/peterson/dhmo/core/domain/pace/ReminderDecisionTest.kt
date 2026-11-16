package casa.peterson.dhmo.core.domain.pace

import casa.peterson.dhmo.core.domain.time.hydrationDay
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random
import org.junit.Assert.assertThrows
import org.junit.Test

class ReminderDecisionTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    /** "HH:mm" on 2026-06-10, or a full local date-time "2026-06-11T00:10", in Berlin. */
    private fun t(s: String): Instant {
        val ldt = if ('T' in s) LocalDateTime.parse(s) else LocalDateTime.parse("2026-06-10T$s")
        return ZonedDateTime.of(ldt, berlin).toInstant()
    }

    private fun tn(s: String?): Instant? = s?.let { t(it) }

    private class Case(
        val now: String,
        val goal: Int,
        val consumed: Int,
        val lastDrink: String? = null,
        val lastReminder: String? = null,
        val snoozedUntil: String? = null,
        val busy: List<Pair<String, String>> = emptyList(),
        val day: LocalDate = LocalDate.parse("2026-06-10"),
        val wake: String = "07:00",
        val sleep: String = "23:00",
        val dayStart: String = "00:00",
        val tolerance: Int = 10,
        val floorMinutes: Long = 60,
        val curve: PaceCurve = PaceCurve.DEFAULT,
        val enabled: Boolean = true,
        val escalation: Boolean = false,
        val summary: Boolean = false,
        val escalationSent: Boolean = false,
        val summarySent: Boolean = false,
    )

    private fun run(c: Case): ReminderDecision {
        val state = ReminderState(
            config = ReminderConfig(
                enabled = c.enabled,
                wake = LocalTime.parse(c.wake),
                sleep = LocalTime.parse(c.sleep),
                intervalFloor = Duration.ofMinutes(c.floorMinutes),
                tolerancePercent = c.tolerance,
                curve = c.curve,
                escalationEnabled = c.escalation,
                endOfDaySummaryEnabled = c.summary,
            ),
            zone = berlin,
            dayStart = LocalTime.parse(c.dayStart),
            day = c.day,
            goalMl = c.goal,
            consumedMl = c.consumed,
            lastDrinkAt = tn(c.lastDrink),
            lastReminderAt = tn(c.lastReminder),
            snoozedUntil = tn(c.snoozedUntil),
            busy = c.busy.map { BusyInterval(t(it.first), t(it.second)) },
            escalationSentToday = c.escalationSent,
            summarySentToday = c.summarySent,
        )
        return nextReminder(state, t(c.now))
    }

    private fun check(c: Case, fire: ReminderKind?, next: String?, kind: AlarmKind?, expected: Int, deficit: Int) {
        val d = run(c)
        assertThat(d.fireNow).isEqualTo(fire)
        assertThat(d.nextAlarmAt).isEqualTo(tn(next))
        assertThat(d.nextAlarmKind).isEqualTo(kind)
        assertThat(d.expectedMl).isEqualTo(expected)
        assertThat(d.deficitMl).isEqualTo(deficit)
    }

    private val tomorrowWake = "2026-06-11T07:00"

    @Test
    fun `R01 before wake`() = check(Case("06:00", 2000, 0), null, "08:05", AlarmKind.NUDGE, 0, 0)

    @Test
    fun `R02 on pace`() = check(Case("09:00", 2000, 500, lastDrink = "08:30"), null, "11:37", AlarmKind.NUDGE, 353, -147)

    @Test
    fun `R03 behind and floor passed`() = check(
        Case("12:00", 2000, 500, lastDrink = "08:30"),
        ReminderKind.NUDGE,
        "13:00",
        AlarmKind.NUDGE,
        752,
        252,
    )

    @Test
    fun `R04 drank 20 minutes ago`() = check(Case("15:00", 2000, 500, lastDrink = "14:40"), null, "15:40", AlarmKind.NUDGE, 1150, 650)

    @Test
    fun `R05 reminded 10 minutes ago`() = check(
        Case("15:00", 2000, 500, lastDrink = "08:30", lastReminder = "14:50"),
        null,
        "15:50",
        AlarmKind.NUDGE,
        1150,
        650,
    )

    @Test
    fun `R06 snoozed`() = check(
        Case("15:00", 2000, 500, lastDrink = "08:30", lastReminder = "13:00", snoozedUntil = "15:20"),
        null,
        "15:20",
        AlarmKind.NUDGE,
        1150,
        650,
    )

    @Test
    fun `R07 goal met`() = check(Case("18:00", 2000, 2000, lastDrink = "17:50"), null, tomorrowWake, AlarmKind.RECHECK, 1548, -452)

    @Test
    fun `R08 goal met with summary on`() = check(
        Case("18:00", 2000, 2100, lastDrink = "17:50", summary = true),
        null,
        "22:30",
        AlarmKind.SUMMARY,
        1548,
        -552,
    )

    @Test
    fun `R09 within tolerance of the full goal`() =
        check(Case("21:30", 2000, 1850, lastDrink = "19:00"), null, tomorrowWake, AlarmKind.RECHECK, 2000, 150)

    @Test
    fun `R10 escalation`() = check(
        Case("21:10", 2000, 900, lastDrink = "16:00", lastReminder = "20:05", escalation = true),
        ReminderKind.ESCALATION,
        "22:10",
        AlarmKind.NUDGE,
        1969,
        1069,
    )

    @Test
    fun `R11 escalation needs 40 percent behind`() = check(
        Case("21:10", 2000, 1200, lastDrink = "16:00", lastReminder = "20:05", escalation = true),
        ReminderKind.NUDGE,
        "22:10",
        AlarmKind.NUDGE,
        1969,
        769,
    )

    @Test
    fun `R12 escalation already sent`() = check(
        Case("22:15", 2000, 900, lastDrink = "16:00", lastReminder = "21:10", escalation = true, escalationSent = true),
        ReminderKind.NUDGE,
        tomorrowWake,
        AlarmKind.RECHECK,
        2000,
        1100,
    )

    @Test
    fun `R13 summary`() = check(
        Case("22:30", 2000, 1500, lastDrink = "20:00", lastReminder = "21:00", summary = true),
        ReminderKind.SUMMARY,
        tomorrowWake,
        AlarmKind.RECHECK,
        2000,
        500,
    )

    @Test
    fun `R14 after sleep`() = check(Case("23:30", 2000, 1500, lastDrink = "20:00"), null, tomorrowWake, AlarmKind.RECHECK, 2000, 500)

    @Test
    fun `R15 busy interval`() = check(
        Case("12:00", 2000, 500, lastDrink = "08:30", busy = listOf("11:30" to "12:45")),
        null,
        "12:45",
        AlarmKind.NUDGE,
        752,
        252,
    )

    @Test
    fun `R16 candidate after sleep`() =
        check(Case("22:20", 2000, 1000, lastDrink = "22:10"), null, tomorrowWake, AlarmKind.RECHECK, 2000, 1000)

    @Test
    fun `R17 zero tolerance`() = check(Case("07:00", 2000, 0, tolerance = 0), null, "07:01", AlarmKind.NUDGE, 0, 0)

    @Test
    fun `R18 disabled`() = check(Case("12:00", 2000, 0, enabled = false), null, null, null, 0, 0)

    @Test
    fun `R19 tolerance of 245 ml`() = check(
        Case("13:24", 2450, 750, lastDrink = "11:00"),
        ReminderKind.NUDGE,
        "14:24",
        AlarmKind.NUDGE,
        1148,
        398,
    )

    @Test
    fun `R20 after midnight inside the hydration day`() {
        val c = Case(
            "2026-06-11T00:10",
            2000,
            1200,
            lastDrink = "2026-06-10T22:00",
            wake = "09:00",
            sleep = "01:00",
            dayStart = "04:00",
        )
        check(c, ReminderKind.NUDGE, "2026-06-11T09:00", AlarmKind.RECHECK, 2000, 800)
    }

    @Test
    fun `R21 after sleep before the day start`() {
        val c = Case(
            "2026-06-11T02:00",
            2000,
            1200,
            lastDrink = "2026-06-10T22:00",
            wake = "09:00",
            sleep = "01:00",
            dayStart = "04:00",
        )
        check(c, null, "2026-06-11T09:00", AlarmKind.RECHECK, 2000, 800)
    }

    @Test
    fun `R22 spring forward day`() {
        val c = Case("2026-03-29T08:30", 2000, 0, day = LocalDate.parse("2026-03-29"), wake = "01:00", sleep = "17:00")
        check(c, ReminderKind.NUDGE, "2026-03-29T09:30", AlarmKind.NUDGE, 1008, 1008)
    }

    @Test
    fun `R23 fall back day`() {
        val c = Case("2026-10-25T08:30", 2000, 0, day = LocalDate.parse("2026-10-25"), wake = "01:00", sleep = "17:00")
        check(c, ReminderKind.NUDGE, "2026-10-25T09:30", AlarmKind.NUDGE, 1150, 1150)
    }

    @Test
    fun `R24 deficit equal to tolerance is not behind`() = check(
        Case("11:00", 3000, 600, lastDrink = "10:30", curve = PaceCurve.LINEAR, tolerance = 5, floorMinutes = 45),
        null,
        "11:15",
        AlarmKind.NUDGE,
        750,
        150,
    )

    @Test
    fun `R25 linear curve nudge`() = check(
        Case("15:00", 3000, 600, lastDrink = "10:30", curve = PaceCurve.LINEAR, tolerance = 5, floorMinutes = 45),
        ReminderKind.NUDGE,
        "15:45",
        AlarmKind.NUDGE,
        1500,
        900,
    )

    @Test
    fun `a state whose day is not the hydration day of now throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            run(Case("12:00", 2000, 0, day = LocalDate.parse("2026-06-09")))
        }
    }

    @Test
    fun `random states keep the invariants`() {
        val rnd = Random(20260610)
        val base = t("2026-06-01T00:00").toEpochMilli()
        repeat(5000) {
            val wake = LocalTime.of(rnd.nextInt(24), rnd.nextInt(4) * 15)
            val sleep = LocalTime.of(rnd.nextInt(24), rnd.nextInt(4) * 15)
            val dayStart = LocalTime.of(rnd.nextInt(24), 0)
            val config = ReminderConfig(
                enabled = true,
                wake = wake,
                sleep = sleep,
                intervalFloor = Duration.ofMinutes(rnd.nextLong(0, 181)),
                tolerancePercent = rnd.nextInt(0, 31),
                curve = if (rnd.nextBoolean()) PaceCurve.DEFAULT else PaceCurve.LINEAR,
                escalationEnabled = rnd.nextBoolean(),
                endOfDaySummaryEnabled = rnd.nextBoolean(),
            )
            // Spans the March and October DST changes of 2026 as well as ordinary days.
            val now = Instant.ofEpochMilli(base + rnd.nextLong(0, 200L * 24 * 3600 * 1000))
            val day = hydrationDay(now, berlin.rules.getOffset(now), dayStart)
            val goal = rnd.nextInt(500, 5000)
            val busyStart = now.plusSeconds(rnd.nextLong(-3 * 3600, 6 * 3600))
            val state = ReminderState(
                config = config,
                zone = berlin,
                dayStart = dayStart,
                day = day,
                goalMl = goal,
                consumedMl = rnd.nextInt(0, goal + 500),
                lastDrinkAt = if (rnd.nextBoolean()) now.minusSeconds(rnd.nextLong(0, 12 * 3600)) else null,
                lastReminderAt = if (rnd.nextBoolean()) now.minusSeconds(rnd.nextLong(0, 12 * 3600)) else null,
                snoozedUntil = if (rnd.nextInt(4) == 0) now.plusSeconds(rnd.nextLong(-3600, 3 * 3600)) else null,
                busy = if (rnd.nextBoolean()) {
                    listOf(BusyInterval(busyStart, busyStart.plusSeconds(rnd.nextLong(60, 4 * 3600))))
                } else {
                    emptyList()
                },
                escalationSentToday = rnd.nextBoolean(),
                summarySentToday = rnd.nextBoolean(),
            )
            val decision = nextReminder(state, now)
            assertThat(nextReminder(state, now)).isEqualTo(decision)
            assertThat(decision.nextAlarmAt).isNotNull()
            assertThat(decision.nextAlarmAt!!).isGreaterThan(now)
            val window = wakeWindow(day, wake, sleep, dayStart, berlin)
            val summaryAt = maxOf(window.wake, window.sleep.minusSeconds(30 * 60))
            val nudgeEnd = if (config.endOfDaySummaryEnabled) summaryAt else window.sleep
            if (decision.fireNow == ReminderKind.NUDGE || decision.fireNow == ReminderKind.ESCALATION) {
                assertThat(now).isAtLeast(window.wake)
                assertThat(now).isLessThan(nudgeEnd)
            }
            if (decision.fireNow == ReminderKind.SUMMARY) {
                assertThat(now).isAtLeast(summaryAt)
                assertThat(now).isLessThan(window.sleep)
            }
        }
    }
}
