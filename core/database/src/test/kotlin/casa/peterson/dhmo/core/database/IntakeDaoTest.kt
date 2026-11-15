package casa.peterson.dhmo.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntakeDaoTest {
    @get:Rule val rule = DatabaseTestRule()
    private val dao get() = rule.db.intakeDao()

    @Test
    fun `observeDay returns live entries in time order`() = runTest {
        dao.upsertAll(listOf(entry(hour = 12), entry(hour = 8), entry(hour = 9, deleted = true), entry(day = DAY.plusDays(1))))
        assertThat(dao.observeDay(DAY).first().map { it.instant }).isInStrictOrder()
        assertThat(dao.observeDay(DAY).first()).hasSize(2)
    }

    @Test
    fun `range queries and totals ignore tombstones`() = runTest {
        dao.upsertAll(listOf(entry(volumeMl = 300), entry(volumeMl = 200, hour = 9), entry(volumeMl = 999, hour = 10, deleted = true)))
        assertThat(dao.totalForDay(DAY)).isEqualTo(500)
        assertThat(dao.totalForDay(DAY.plusDays(3))).isEqualTo(0)
        assertThat(dao.getRange(DAY, DAY.plusDays(1))).hasSize(2)
        assertThat(dao.lastDrinkInstant(DAY)).isEqualTo(at(DAY, 9))
        assertThat(dao.observeCount().first()).isEqualTo(2)
    }

    @Test
    fun `soft delete and restore`() = runTest {
        val e = entry()
        dao.upsert(e)
        val now = Instant.parse("2026-06-10T20:00:00Z")
        dao.softDelete(e.id, now)
        assertThat(dao.getById(e.id)!!.deletedAt).isEqualTo(now)
        assertThat(dao.observeDay(DAY).first()).isEmpty()
        dao.restore(e.id, now.plusSeconds(5))
        val restored = dao.getById(e.id)!!
        assertThat(restored.deletedAt).isNull()
        assertThat(restored.updatedAt).isEqualTo(now.plusSeconds(5))
    }

    @Test
    fun `hc record id is unique but may be null many times`() = runTest {
        dao.upsert(entry())
        dao.upsert(entry(hour = 9))
        val e = entry(hour = 10)
        dao.upsert(e)
        dao.setHcRecordId(e.id, "hc-1")
        assertThat(dao.getByHcRecordId("hc-1")!!.id).isEqualTo(e.id)
        assertThat(dao.getById(e.id)!!.updatedAt).isEqualTo(e.updatedAt)
    }

    @Test
    fun `rebucketing helpers include tombstones`() = runTest {
        val old = entry(day = DAY.minusDays(1))
        val gone = entry(deleted = true)
        dao.upsertAll(listOf(old, gone, entry(day = DAY.plusDays(1))))
        assertThat(dao.entriesFromDay(DAY)).hasSize(2)
        dao.updateHydrationDay(gone.id, DAY.minusDays(5))
        assertThat(dao.getById(gone.id)!!.hydrationDay).isEqualTo(DAY.minusDays(5))
    }

    @Test
    fun `imports queries`() = runTest {
        val live = entry(source = casa.peterson.dhmo.core.model.Source.HEALTH_CONNECT_IMPORT, hcDataOrigin = "app.a", hcRecordId = "r1")
        val adopted = live.copy(id = UUID.randomUUID(), hcRecordId = "r2", hcAdopted = true)
        dao.upsertAll(listOf(live, adopted, entry()))
        assertThat(dao.liveImportsFrom("app.a").map { it.id }).containsExactly(live.id)
        assertThat(dao.importsBetween(at(DAY, 0), at(DAY, 23))).hasSize(2)
        assertThat(dao.getAllIncludingDeleted()).hasSize(3)
        dao.deleteAll()
        assertThat(dao.getAllIncludingDeleted()).isEmpty()
    }
}
