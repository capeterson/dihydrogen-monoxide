package casa.peterson.dhmo.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.database.entity.HealthDailyEntity
import casa.peterson.dhmo.core.database.entity.WeatherDayEntity
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherAndHealthDailyDaoTest {
    @get:Rule val rule = DatabaseTestRule()

    private fun weather(day: java.time.LocalDate) = WeatherDayEntity(day, 525, 134, 30.0, 33.5, null, 500, Instant.EPOCH)

    @Test
    fun `weather rows`() = runTest {
        val dao = rule.db.weatherDao()
        dao.upsertAll(listOf(weather(DAY), weather(DAY.plusDays(1))))
        dao.upsert(weather(DAY).copy(extraMl = 750))
        assertThat(dao.getDay(DAY)!!.extraMl).isEqualTo(750)
        assertThat(dao.observeDay(DAY).first()!!.meanRelHumidity).isNull()
        assertThat(dao.getRange(DAY, DAY.plusDays(5))).hasSize(2)
        assertThat(dao.getAll()).hasSize(2)
        dao.deleteAll()
        assertThat(dao.getAll()).isEmpty()
    }

    @Test
    fun `health daily rows`() = runTest {
        val dao = rule.db.healthDailyDao()
        dao.upsertAll(
            listOf(HealthDailyEntity(DAY, 8000, 300.5, Instant.EPOCH), HealthDailyEntity(DAY.plusDays(1), null, null, Instant.EPOCH)),
        )
        dao.upsertAll(listOf(HealthDailyEntity(DAY, 9000, 300.5, Instant.EPOCH)))
        assertThat(dao.getRange(DAY, DAY.plusDays(1)).map { it.steps }).containsExactly(9000L, null).inOrder()
        assertThat(dao.getAll()).hasSize(2)
        dao.deleteAll()
        assertThat(dao.getAll()).isEmpty()
    }
}
