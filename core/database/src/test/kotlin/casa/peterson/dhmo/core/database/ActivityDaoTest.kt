package casa.peterson.dhmo.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.database.entity.ActivityAdjustmentEntity
import casa.peterson.dhmo.core.model.ActivitySource
import casa.peterson.dhmo.core.model.Intensity
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityDaoTest {
    @get:Rule val rule = DatabaseTestRule()
    private val dao get() = rule.db.activityDao()

    private fun row(id: String, day: java.time.LocalDate, minutes: Int, source: ActivitySource = ActivitySource.MANUAL_PLANNED) =
        ActivityAdjustmentEntity(source, id, day, minutes, Intensity.MODERATE, minutes * 12, null, Instant.EPOCH)

    @Test
    fun `composite key upsert, delete and minutes per day`() = runTest {
        dao.upsertAll(listOf(row("a", DAY, 20), row("b", DAY, 10), row("c", DAY.plusDays(1), 5)))
        dao.upsert(row("a", DAY, 30))
        assertThat(dao.getDay(DAY).map { it.minutes }).containsExactly(30, 10).inOrder()
        assertThat(dao.observeDay(DAY).first()).hasSize(2)
        assertThat(dao.minutesPerDay(DAY, DAY.plusDays(1)).associate { it.date to it.minutes })
            .containsExactly(DAY, 40, DAY.plusDays(1), 5)
        dao.delete("MANUAL_PLANNED", "b")
        assertThat(dao.getDay(DAY)).hasSize(1)
        assertThat(dao.getAll()).hasSize(2)
        dao.deleteAll()
        assertThat(dao.getAll()).isEmpty()
    }

    @Test
    fun `same record id from different sources are distinct`() = runTest {
        dao.upsertAll(listOf(row("x", DAY, 1), row("x", DAY, 2, ActivitySource.HEALTH_CONNECT)))
        assertThat(dao.getAll()).hasSize(2)
    }
}
