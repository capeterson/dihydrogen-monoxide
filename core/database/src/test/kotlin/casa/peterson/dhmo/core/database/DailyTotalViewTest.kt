package casa.peterson.dhmo.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyTotalViewTest {
    @get:Rule val rule = DatabaseTestRule()

    @Test
    fun `groups by stored hydration day and excludes tombstones`() = runTest {
        rule.db.intakeDao().upsertAll(
            listOf(
                entry(hour = 8, volumeMl = 250),
                entry(hour = 13, volumeMl = 500),
                entry(hour = 15, volumeMl = 9000, deleted = true),
                entry(day = DAY.plusDays(1), hour = 1, volumeMl = 100),
            ),
        )
        val dao = rule.db.dailyTotalDao()
        val total = dao.observeDay(DAY).first()!!
        assertThat(total.totalMl).isEqualTo(750)
        assertThat(total.entryCount).isEqualTo(2)
        assertThat(total.firstAt).isEqualTo(at(DAY, 8))
        assertThat(total.lastAt).isEqualTo(at(DAY, 13))
        assertThat(dao.getRange(DAY, DAY.plusDays(1)).map { it.totalMl }).containsExactly(750, 100).inOrder()
        assertThat(dao.firstDay()).isEqualTo(DAY)
        assertThat(dao.observeRange(DAY, DAY).first()).hasSize(1)
    }

    @Test
    fun `a day with only deleted entries has no row`() = runTest {
        rule.db.intakeDao().upsert(entry(deleted = true))
        assertThat(rule.db.dailyTotalDao().observeDay(DAY).first()).isNull()
        assertThat(rule.db.dailyTotalDao().firstDay()).isNull()
    }
}
