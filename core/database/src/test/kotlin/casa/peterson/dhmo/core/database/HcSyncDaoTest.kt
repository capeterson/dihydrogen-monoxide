package casa.peterson.dhmo.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.database.entity.HcOp
import casa.peterson.dhmo.core.database.entity.HcPendingOpEntity
import casa.peterson.dhmo.core.database.entity.SyncStateEntity
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HcSyncDaoTest {
    @get:Rule val rule = DatabaseTestRule()
    private val dao get() = rule.db.hcSyncDao()

    private fun op(entry: UUID, kind: HcOp) = HcPendingOpEntity(entryId = entry, op = kind, enqueuedAt = Instant.EPOCH)

    @Test
    fun `enqueue keeps only the latest op per entry and stays fifo`() = runTest {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        dao.enqueue(op(a, HcOp.UPSERT))
        dao.enqueue(op(b, HcOp.UPSERT))
        dao.enqueue(op(a, HcOp.DELETE))
        val ops = dao.nextOps(10)
        assertThat(ops.map { it.entryId to it.op }).containsExactly(b to HcOp.UPSERT, a to HcOp.DELETE).inOrder()
        assertThat(dao.nextOps(1)).hasSize(1)
        assertThat(dao.observePendingCount().first()).isEqualTo(2)
    }

    @Test
    fun `failures are counted and ops can be deleted or cleared`() = runTest {
        val a = UUID.randomUUID()
        dao.enqueue(op(a, HcOp.UPSERT))
        val id = dao.nextOps(1).single().id
        dao.markFailed(id, "rate limited")
        dao.markFailed(id, "rate limited")
        val failed = dao.nextOps(1).single()
        assertThat(failed.attempts).isEqualTo(2)
        assertThat(failed.lastError).isEqualTo("rate limited")
        dao.deleteOps(listOf(id))
        assertThat(dao.nextOps(10)).isEmpty()
        dao.enqueue(op(a, HcOp.UPSERT))
        dao.clearOps()
        assertThat(dao.nextOps(10)).isEmpty()
    }

    @Test
    fun `sync state key value`() = runTest {
        assertThat(dao.getState("hc.changes_token")).isNull()
        dao.putState(SyncStateEntity("hc.changes_token", "t1", Instant.EPOCH))
        dao.putState(SyncStateEntity("hc.changes_token", "t2", Instant.EPOCH))
        assertThat(dao.getState("hc.changes_token")).isEqualTo("t2")
        dao.deleteState("hc.changes_token")
        assertThat(dao.getState("hc.changes_token")).isNull()
    }
}
