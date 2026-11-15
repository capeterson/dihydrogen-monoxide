package casa.peterson.dhmo.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.data.RoomTestEnvironment
import casa.peterson.dhmo.core.model.ChangeOrigin
import casa.peterson.dhmo.core.model.EntryChange
import casa.peterson.dhmo.core.model.Source
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.FixedZoneProvider
import casa.peterson.dhmo.core.testing.MutableClock
import casa.peterson.dhmo.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** TECHNICAL_PLAN §9.14, dataset H: bucketing across DST, travel and a day-start change. */
@RunWith(AndroidJUnit4::class)
class DayStartChangeTest {
    @get:Rule val env = RoomTestEnvironment()

    private val clock = MutableClock(Instant.parse("2026-06-11T00:00:00Z"))
    private val zone = FixedZoneProvider(ZoneOffset.of("+02:00"))
    private val settings = FakeSettingsRepository()
    private val received = mutableListOf<EntryChange>()
    private val recorder = object : EntryChangeListener {
        override suspend fun onEntriesChanged(change: EntryChange) {
            received += change
        }
    }

    private fun repository() = OfflineIntakeRepository(
        intakeDao = env.db.intakeDao(),
        dailyTotalDao = env.db.dailyTotalDao(),
        hcSyncDao = env.db.hcSyncDao(),
        transactions = env.transactions,
        settings = settings,
        clock = clock,
        zoneProvider = zone,
        listeners = setOf(recorder),
    )

    private val jun9 = LocalDate.of(2026, 6, 9)
    private val jun10 = LocalDate.of(2026, 6, 10)
    private val jun11 = LocalDate.of(2026, 6, 11)

    @Test
    fun `changing the day start re-buckets only the old today and later`() = runTest {
        val repo = repository()
        val instants = TestData.dayStartChangeInstants
        val a = repo.add(250, Source.MANUAL, instants.getValue("a"))
        val b = repo.add(250, Source.MANUAL, instants.getValue("b"))
        val c = repo.add(250, Source.MANUAL, instants.getValue("c"))
        assertThat(listOf(a, b, c).map { it.hydrationDay }).containsExactly(jun10, jun10, jun11).inOrder()
        received.clear()

        // 2026-06-11T00:00:00Z: the old today is 2026-06-11 and the setting moves from 00:00 to 04:00.
        settings.update { it.copy(dayStartMinutes = 4 * 60) }
        repo.rebucketFrom(oldToday = jun11, newDayStart = LocalTime.of(4, 0))
        val d = repo.add(250, Source.MANUAL, instants.getValue("d"))

        assertThat(repo.get(a.id)!!.hydrationDay).isEqualTo(jun10) // would be 9 Jun under the new rule, but is history
        assertThat(repo.get(b.id)!!.hydrationDay).isEqualTo(jun10)
        assertThat(repo.get(c.id)!!.hydrationDay).isEqualTo(jun10) // re-bucketed
        assertThat(d.hydrationDay).isEqualTo(jun11)
        assertThat(repo.dailyTotals(jun9, jun11).associate { it.date to it.totalMl })
            .containsExactly(jun10, 750, jun11, 250)
        assertThat(received.first()).isEqualTo(EntryChange(setOf(jun10, jun11), ChangeOrigin.LOCAL))
    }

    @Test
    fun `re-bucketing also moves tombstones and leaves updatedAt alone`() = runTest {
        val repo = repository()
        val c = repo.add(250, Source.MANUAL, TestData.dayStartChangeInstants.getValue("c"))
        repo.delete(c.id)
        val before = repo.get(c.id)!!

        repo.rebucketFrom(jun11, LocalTime.of(4, 0))

        val after = repo.get(c.id)!!
        assertThat(after.hydrationDay).isEqualTo(jun10)
        assertThat(after.updatedAt).isEqualTo(before.updatedAt)
        assertThat(after.deletedAt).isEqualTo(before.deletedAt)
    }

    @Test
    fun `re-bucketing with nothing to move notifies nobody`() = runTest {
        val repo = repository()
        repo.add(250, Source.MANUAL, TestData.dayStartChangeInstants.getValue("b"))
        received.clear()
        repo.rebucketFrom(jun11, LocalTime.of(4, 0))
        assertThat(received).isEmpty()
    }

    @Test
    fun `dataset H1 to H6 give 250, 1000 and 250 ml per day`() = runTest {
        val repo = repository()
        TestData.datasetH.take(6).forEach { case ->
            zone.zone = case.offset
            val entry = repo.add(250, Source.MANUAL, case.instant)
            assertThat(entry.hydrationDay).isEqualTo(case.expectedDay)
        }
        val totals = repo.dailyTotals(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 26))
        assertThat(totals.map { it.date to (it.totalMl to it.entryCount) }).containsExactly(
            LocalDate.of(2026, 10, 24) to (250 to 1),
            LocalDate.of(2026, 10, 25) to (1000 to 4),
            LocalDate.of(2026, 10, 26) to (250 to 1),
        ).inOrder()
    }

    @Test
    fun `dataset H7 to H12 bucket with the day start and offset in force when logged`() = runTest {
        val repo = repository()
        TestData.datasetH.drop(6).forEach { case ->
            zone.zone = case.offset
            settings.update { it.copy(dayStartMinutes = case.dayStart.toSecondOfDay() / 60) }
            assertThat(repo.add(250, Source.MANUAL, case.instant).hydrationDay).isEqualTo(case.expectedDay)
        }
    }
}
