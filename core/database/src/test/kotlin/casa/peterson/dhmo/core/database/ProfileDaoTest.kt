package casa.peterson.dhmo.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.database.entity.ProfileSnapshotEntity
import casa.peterson.dhmo.core.model.ActivityLevel
import casa.peterson.dhmo.core.model.Climate
import casa.peterson.dhmo.core.model.Gender
import casa.peterson.dhmo.core.model.PregnancyStatus
import casa.peterson.dhmo.core.model.ProfileSource
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileDaoTest {
    @get:Rule val rule = DatabaseTestRule()
    private val dao get() = rule.db.profileDao()

    private fun snapshot(from: LocalDate, createdSecond: Long, source: ProfileSource = ProfileSource.MANUAL) = ProfileSnapshotEntity(
        id = UUID.randomUUID(),
        effectiveFrom = from,
        birthDate = LocalDate.of(1990, 1, 1),
        gender = Gender.FEMALE,
        weightKg = 60.0,
        activityLevel = ActivityLevel.LIGHT,
        climate = Climate.TEMPERATE,
        pregnancyStatus = PregnancyStatus.NONE,
        lactating = false,
        highAltitude = false,
        source = source,
        createdAt = Instant.ofEpochSecond(createdSecond),
    )

    @Test
    fun `latest is the greatest effective_from then created_at`() = runTest {
        val newest = snapshot(DAY, 5)
        dao.insertAll(listOf(snapshot(DAY.minusDays(3), 99), snapshot(DAY, 1), newest))
        assertThat(dao.observeLatest().first()!!.id).isEqualTo(newest.id)
        assertThat(dao.getAll().map { it.effectiveFrom }).isInOrder()
        assertThat(dao.observeAll().first()).hasSize(3)
    }

    @Test
    fun `deleteManualOn only removes manual rows of that day`() = runTest {
        dao.insertAll(listOf(snapshot(DAY, 1), snapshot(DAY, 2, ProfileSource.HEALTH_CONNECT), snapshot(DAY.plusDays(1), 3)))
        dao.deleteManualOn(DAY)
        assertThat(dao.getAll().map { it.source to it.effectiveFrom })
            .containsExactly(ProfileSource.HEALTH_CONNECT to DAY, ProfileSource.MANUAL to DAY.plusDays(1))
        dao.deleteAll()
        assertThat(dao.getAll()).isEmpty()
    }
}
