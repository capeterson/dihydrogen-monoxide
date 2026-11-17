package casa.peterson.dhmo.core.data.repository

import androidx.paging.PagingSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import casa.peterson.dhmo.core.data.RoomTestEnvironment
import casa.peterson.dhmo.core.data.mapper.toEntity
import casa.peterson.dhmo.core.data.usecase.intake.EntryNotFoundException
import casa.peterson.dhmo.core.database.entity.HcOp
import casa.peterson.dhmo.core.model.ChangeOrigin
import casa.peterson.dhmo.core.model.EntryChange
import casa.peterson.dhmo.core.model.Source
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.settings.HealthConnectSettings
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.FixedZoneProvider
import casa.peterson.dhmo.core.testing.MutableClock
import casa.peterson.dhmo.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineIntakeRepositoryTest {
    @get:Rule val env = RoomTestEnvironment()

    private val start = Instant.parse("2026-06-10T08:00:00Z")
    private val clock = MutableClock(start)
    private val zone = FixedZoneProvider() // Europe/Berlin, +02:00 in June
    private val settings = FakeSettingsRepository()
    private val received = mutableListOf<EntryChange>()
    private val recorder = object : EntryChangeListener {
        override suspend fun onEntriesChanged(change: EntryChange) {
            received += change
        }
    }

    private fun repository(vararg listeners: EntryChangeListener) = OfflineIntakeRepository(
        intakeDao = env.db.intakeDao(),
        dailyTotalDao = env.db.dailyTotalDao(),
        hcSyncDao = env.db.hcSyncDao(),
        transactions = env.transactions,
        settings = settings,
        clock = clock,
        zoneProvider = zone,
        listeners = listeners.toSet(),
    )

    private suspend fun failure(block: suspend () -> Unit): Throwable? = runCatching { block() }.exceptionOrNull()

    private val day10 = LocalDate.of(2026, 6, 10)
    private val day11 = LocalDate.of(2026, 6, 11)

    @Test
    fun `paging groups hydration days before ordering by instant`() = runTest {
        val olderBucketNewerInstant = TestData.entry(Instant.parse("2026-06-11T03:00:00Z")).copy(hydrationDay = day10)
        val newerBucketOlderInstant = TestData.entry(Instant.parse("2026-06-11T01:00:00Z")).copy(hydrationDay = day11)
        val newestInOlderBucket = TestData.entry(Instant.parse("2026-06-11T04:00:00Z")).copy(hydrationDay = day10)
        env.db.intakeDao().upsertAll(listOf(olderBucketNewerInstant, newerBucketOlderInstant, newestInOlderBucket).map { it.toEntity() })
        val page = env.db.intakeDao().pagingSource().load(PagingSource.LoadParams.Refresh(null, 20, false))
            as PagingSource.LoadResult.Page
        assertThat(page.data.map { it.id }).containsExactly(
            newerBucketOlderInstant.id,
            newestInOlderBucket.id,
            olderBucketNewerInstant.id,
        ).inOrder()
    }

    @Test
    fun `add stores the hydration day with the current day start and the current offset`() = runTest {
        settings.update { it.copy(dayStartMinutes = 4 * 60) }
        val repo = repository()
        val early = repo.add(250, Source.MANUAL, at = Instant.parse("2026-06-11T01:30:00Z"), note = "tea") // 03:30 local
        val later = repo.add(300, Source.WIDGET, at = Instant.parse("2026-06-11T02:00:00Z"))

        assertThat(early.hydrationDay).isEqualTo(day10)
        assertThat(early.zoneOffset.totalSeconds).isEqualTo(2 * 3600)
        assertThat(early.createdAt).isEqualTo(start)
        assertThat(early.updatedAt).isEqualTo(start)
        assertThat(early.deletedAt).isNull()
        assertThat(early.note).isEqualTo("tea")
        assertThat(later.hydrationDay).isEqualTo(day11)
        assertThat(repo.get(early.id)).isEqualTo(early)
    }

    @Test
    fun `add without a time uses the clock`() = runTest {
        val entry = repository().add(500, Source.NOTIFICATION)
        assertThat(entry.instant).isEqualTo(start)
        assertThat(entry.hydrationDay).isEqualTo(day10)
    }

    @Test
    fun `add rejects volumes outside 1 to 10000`() = runTest {
        val repo = repository()
        assertThat(failure { repo.add(0, Source.MANUAL) }).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(failure { repo.add(10_001, Source.MANUAL) }).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(repo.add(10_000, Source.MANUAL).volumeMl).isEqualTo(10_000)
        assertThat(repo.add(1, Source.MANUAL).volumeMl).isEqualTo(1)
    }

    @Test
    fun `update recomputes the day and bumps updatedAt`() = runTest {
        val repo = repository()
        val entry = repo.add(250, Source.MANUAL)
        clock.advanceBy(Duration.ofMinutes(5))
        val moved = repo.update(entry.id, 400, Instant.parse("2026-06-10T23:00:00Z"), "evening") // 01:00 on the 11th

        assertThat(moved.hydrationDay).isEqualTo(day11)
        assertThat(moved.volumeMl).isEqualTo(400)
        assertThat(moved.updatedAt).isEqualTo(start.plus(Duration.ofMinutes(5)))
        assertThat(moved.createdAt).isEqualTo(start)
        assertThat(repo.get(entry.id)).isEqualTo(moved)
    }

    @Test
    fun `update with an unchanged instant keeps the stored bucket and offset`() = runTest {
        val repo = repository()
        val entry = repo.add(250, Source.MANUAL)
        zone.zone = java.time.ZoneId.of("America/New_York")
        settings.update { it.copy(dayStartMinutes = 12 * 60) }
        val edited = repo.update(entry.id, 350, entry.instant, "note")

        assertThat(edited.hydrationDay).isEqualTo(entry.hydrationDay)
        assertThat(edited.zoneOffset).isEqualTo(entry.zoneOffset)
    }

    @Test
    fun `update of a missing or deleted entry fails`() = runTest {
        val repo = repository()
        assertThat(failure { repo.update(UUID.randomUUID(), 100, start, null) }).isInstanceOf(EntryNotFoundException::class.java)
        val entry = repo.add(250, Source.MANUAL)
        repo.delete(entry.id)
        assertThat(failure { repo.update(entry.id, 100, start, null) }).isInstanceOf(EntryNotFoundException::class.java)
    }

    @Test
    fun `delete tombstones and undo restores`() = runTest {
        val repo = repository()
        val entry = repo.add(250, Source.MANUAL)
        clock.advanceBy(Duration.ofMinutes(1))
        repo.delete(entry.id)

        val deleted = repo.get(entry.id)!!
        assertThat(deleted.deletedAt).isEqualTo(clock.instant())
        assertThat(deleted.updatedAt).isEqualTo(clock.instant())
        assertThat(repo.entries(day10, day10)).isEmpty()
        assertThat(repo.dailyTotals(day10, day10)).isEmpty()

        clock.advanceBy(Duration.ofMinutes(1))
        repo.undoDelete(entry.id)
        val restored = repo.get(entry.id)!!
        assertThat(restored.deletedAt).isNull()
        assertThat(restored.updatedAt).isEqualTo(clock.instant())
        assertThat(repo.dailyTotals(day10, day10).single().totalMl).isEqualTo(250)
    }

    @Test
    fun `observers and daily totals follow the writes`() = runTest {
        val repo = repository()
        repo.observeDailyTotal(day10).test {
            assertThat(awaitItem()).isNull()
            repo.add(250, Source.MANUAL)
            assertThat(awaitItem()!!.totalMl).isEqualTo(250)
            repo.add(500, Source.MANUAL)
            val total = awaitItem()!!
            assertThat(total.totalMl).isEqualTo(750)
            assertThat(total.entryCount).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repo.firstDay()).isEqualTo(day10)
        assertThat(repo.observeDay(day10).first()).hasSize(2)
    }

    @Test
    fun `listeners are called once per write with the affected days`() = runTest {
        val repo = repository(recorder)
        val entry = repo.add(250, Source.MANUAL)
        assertThat(received).containsExactly(EntryChange(setOf(day10), ChangeOrigin.LOCAL))

        received.clear()
        repo.update(entry.id, 250, Instant.parse("2026-06-10T23:00:00Z"), null)
        assertThat(received).containsExactly(EntryChange(setOf(day10, day11), ChangeOrigin.LOCAL))

        received.clear()
        repo.delete(entry.id)
        assertThat(received).containsExactly(EntryChange(setOf(day11), ChangeOrigin.LOCAL))

        received.clear()
        repo.undoDelete(entry.id)
        assertThat(received).containsExactly(EntryChange(setOf(day11), ChangeOrigin.LOCAL))

        received.clear()
        repo.notifyExternalChange(setOf(day10), ChangeOrigin.HEALTH_CONNECT)
        assertThat(received).containsExactly(EntryChange(setOf(day10), ChangeOrigin.HEALTH_CONNECT))
    }

    @Test
    fun `a failed write does not notify`() = runTest {
        val repo = repository(recorder)
        assertThat(failure { repo.add(0, Source.MANUAL) }).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(received).isEmpty()
    }

    @Test
    fun `a throwing listener does not fail the write or starve the others`() = runTest {
        val broken = object : EntryChangeListener {
            override suspend fun onEntriesChanged(change: EntryChange) = error("boom")
        }
        val repo = repository(broken, recorder)
        val entry = repo.add(250, Source.MANUAL)
        assertThat(repo.get(entry.id)).isNotNull()
        assertThat(received).hasSize(1)
    }

    @Test
    fun `pending operations are written only when writeHydration is on`() = runTest {
        val repo = repository()
        val off = repo.add(250, Source.MANUAL)
        repo.update(off.id, 300, start, null)
        repo.delete(off.id)
        assertThat(env.db.hcSyncDao().nextOps(10)).isEmpty()

        settings.update { it.copy(healthConnect = HealthConnectSettings(writeHydration = true)) }
        val entry = repo.add(250, Source.MANUAL)
        assertThat(env.db.hcSyncDao().nextOps(10).map { it.entryId to it.op }).containsExactly(entry.id to HcOp.UPSERT)

        repo.update(entry.id, 275, start, null)
        assertThat(env.db.hcSyncDao().nextOps(10).map { it.op }).containsExactly(HcOp.UPSERT) // latest state wins

        repo.delete(entry.id)
        val afterDelete = env.db.hcSyncDao().nextOps(10)
        assertThat(afterDelete.map { it.entryId to it.op }).containsExactly(entry.id to HcOp.DELETE)
        assertThat(afterDelete.single().enqueuedAt).isEqualTo(clock.instant())

        repo.undoDelete(entry.id)
        assertThat(env.db.hcSyncDao().nextOps(10).map { it.op }).containsExactly(HcOp.UPSERT)
    }

    @Test
    fun `imports never get a pending operation`() = runTest {
        settings.update { it.copy(healthConnect = HealthConnectSettings(writeHydration = true)) }
        val repo = repository()
        val import = TestData.importedEntry(start)
        env.db.intakeDao().upsert(import.toEntity())

        repo.adopt(import.id)
        repo.update(import.id, 350, start, null)
        repo.delete(import.id)
        repo.undoDelete(import.id)
        repo.add(100, Source.HEALTH_CONNECT_IMPORT)

        assertThat(env.db.hcSyncDao().nextOps(10)).isEmpty()
    }

    @Test
    fun `adopt makes an import editable and notifies`() = runTest {
        val repo = repository(recorder)
        val import = TestData.importedEntry(start)
        env.db.intakeDao().upsert(import.toEntity())
        assertThat(repo.get(import.id)!!.isEditable).isFalse()

        clock.advanceBy(Duration.ofMinutes(2))
        repo.adopt(import.id)

        val adopted = repo.get(import.id)!!
        assertThat(adopted.isEditable).isTrue()
        assertThat(adopted.updatedAt).isEqualTo(clock.instant())
        assertThat(received).containsExactly(EntryChange(setOf(import.hydrationDay), ChangeOrigin.LOCAL))

        assertThat(failure { repo.adopt(repo.add(100, Source.MANUAL).id) }).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a drink from the watch is stored once with its own id, time and offset`() = runTest {
        settings.update { it.copy(healthConnect = HealthConnectSettings(writeHydration = true)) }
        val repo = repository(recorder)
        val id = UUID.randomUUID()
        val at = Instant.parse("2026-06-09T21:30:00Z") // 23:30 in Berlin on June 9

        assertThat(repo.addFromWatch(id, 330, at, java.time.ZoneOffset.ofHours(2))).isTrue()
        assertThat(repo.addFromWatch(id, 330, at, java.time.ZoneOffset.ofHours(2))).isFalse()

        val row = env.db.intakeDao().getById(id)!!
        assertThat(row.source).isEqualTo(Source.WEAR)
        assertThat(row.instant).isEqualTo(at)
        assertThat(row.hydrationDay).isEqualTo(LocalDate.of(2026, 6, 9))
        assertThat(env.db.hcSyncDao().allOps().map { it.entryId }).containsExactly(id)
        assertThat(received).containsExactly(EntryChange(setOf(LocalDate.of(2026, 6, 9)), ChangeOrigin.LOCAL))
    }
}
