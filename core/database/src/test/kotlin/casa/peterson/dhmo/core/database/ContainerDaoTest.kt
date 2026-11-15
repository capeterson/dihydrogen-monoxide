package casa.peterson.dhmo.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.database.entity.ContainerEntity
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContainerDaoTest {
    @get:Rule val rule = DatabaseTestRule()
    private val dao get() = rule.db.containerDao()

    @Test
    fun `orders by sort order then name and hides tombstones`() = runTest {
        val a = container("B", 1)
        val b = container("A", 1)
        val c = container("C", 0)
        val d = container("D", 2)
        dao.upsertAll(listOf(a, b, c, d))
        dao.softDelete(d.id, Instant.EPOCH.plusSeconds(1))
        assertThat(dao.observeAll().first().map(ContainerEntity::name)).containsExactly("C", "A", "B").inOrder()
        assertThat(dao.getAll()).hasSize(3)
        assertThat(dao.countIncludingDeleted()).isEqualTo(4)
        assertThat(dao.getAllIncludingDeleted()).hasSize(4)
        assertThat(dao.getById(d.id)!!.deletedAt).isNotNull()
        dao.deleteAll()
        assertThat(dao.countIncludingDeleted()).isEqualTo(0)
    }
}
