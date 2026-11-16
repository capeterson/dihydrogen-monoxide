package casa.peterson.dhmo.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import casa.peterson.dhmo.core.data.RoomTestEnvironment
import casa.peterson.dhmo.core.data.mapper.toEntity
import casa.peterson.dhmo.core.model.ProfileSource
import casa.peterson.dhmo.core.model.settings.PinnedInputs
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.MutableClock
import casa.peterson.dhmo.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineProfileRepositoryTest {
    @get:Rule val env = RoomTestEnvironment()

    private val clock = MutableClock(Instant.parse("2026-06-10T08:00:00Z"))
    private val settings = FakeSettingsRepository()

    private fun repository() = OfflineProfileRepository(env.db.profileDao(), env.transactions, settings, clock)

    private val jan1 = LocalDate.of(2026, 1, 1)
    private val mar1 = LocalDate.of(2026, 3, 1)
    private val may1 = LocalDate.of(2026, 5, 1)
    private val light = TestData.adultProfile.copy(weightKg = 60.0)
    private val heavy = TestData.adultProfile.copy(weightKg = 70.0)

    @Test
    fun `resolution picks the latest snapshot on or before the day`() = runTest {
        val repo = repository()
        repo.save(light, jan1)
        repo.save(heavy, mar1)

        assertThat(repo.profileFor(LocalDate.of(2026, 2, 28))!!.profile.weightKg).isEqualTo(60.0)
        assertThat(repo.profileFor(mar1)!!.profile.weightKg).isEqualTo(70.0)
        assertThat(repo.profileFor(LocalDate.of(2026, 12, 1))!!.profile.weightKg).isEqualTo(70.0)
    }

    @Test
    fun `dates before the first profile use the earliest one`() = runTest {
        val repo = repository()
        repo.save(light, mar1)
        repo.save(heavy, may1)
        assertThat(repo.profileFor(jan1)!!.profile.weightKg).isEqualTo(60.0)
    }

    @Test
    fun `no profile resolves to null`() = runTest {
        assertThat(repository().profileFor(jan1)).isNull()
    }

    @Test
    fun `a second manual save on the same day replaces the first`() = runTest {
        val repo = repository()
        repo.save(light, mar1)
        clock.advanceBy(Duration.ofHours(1))
        repo.save(heavy, mar1)

        val snapshots = env.db.profileDao().getAll()
        assertThat(snapshots).hasSize(1)
        assertThat(snapshots.single().weightKg).isEqualTo(70.0)
        assertThat(snapshots.single().source).isEqualTo(ProfileSource.MANUAL)
    }

    @Test
    fun `a manual save keeps rows of other days and Health Connect rows of the same day`() = runTest {
        val repo = repository()
        repo.save(light, jan1)
        repo.addHealthConnectWeight(62.5, mar1)
        repo.save(heavy, mar1)

        val rows = env.db.profileDao().getAll()
        assertThat(rows.map { it.source }).containsExactly(ProfileSource.MANUAL, ProfileSource.HEALTH_CONNECT, ProfileSource.MANUAL)
    }

    @Test
    fun `a Health Connect weight copies the latest snapshot and changes only the weight`() = runTest {
        val repo = repository()
        repo.save(TestData.adultProfile.copy(weightKg = 60.0, highAltitude = true), jan1)
        clock.advanceBy(Duration.ofDays(1))
        repo.addHealthConnectWeight(64.2, mar1)

        val resolved = repo.profileFor(mar1)!!
        assertThat(resolved.source).isEqualTo(ProfileSource.HEALTH_CONNECT)
        assertThat(resolved.profile.weightKg).isEqualTo(64.2)
        assertThat(resolved.profile.highAltitude).isTrue()
        assertThat(resolved.createdAt).isEqualTo(clock.instant())
    }

    @Test
    fun `a Health Connect weight without any profile is ignored`() = runTest {
        val repo = repository()
        repo.addHealthConnectWeight(64.2, mar1)
        assertThat(env.db.profileDao().getAll()).isEmpty()
    }

    @Test
    fun `pinWeight skips Health Connect snapshots`() = runTest {
        val repo = repository()
        repo.save(light, jan1)
        repo.addHealthConnectWeight(64.0, mar1)
        assertThat(repo.profileFor(may1)!!.profile.weightKg).isEqualTo(64.0)

        settings.update { it.copy(goal = it.goal.copy(pinned = PinnedInputs(pinWeight = true))) }
        val pinned = repo.profileFor(may1)!!
        assertThat(pinned.profile.weightKg).isEqualTo(60.0)
        assertThat(pinned.source).isEqualTo(ProfileSource.MANUAL)
    }

    @Test
    fun `pinned weight with a later manual snapshot prefers the manual one`() = runTest {
        val repo = repository()
        repo.save(light, jan1)
        repo.addHealthConnectWeight(64.0, mar1)
        repo.save(heavy, may1)
        settings.update { it.copy(goal = it.goal.copy(pinned = PinnedInputs(pinWeight = true))) }

        assertThat(repo.profileFor(LocalDate.of(2026, 4, 1))!!.profile.weightKg).isEqualTo(60.0)
        assertThat(repo.profileFor(may1)!!.profile.weightKg).isEqualTo(70.0)
    }

    @Test
    fun `ties on the effective day are broken by creation time`() = runTest {
        val first = TestData.snapshot(mar1, light, createdAt = Instant.parse("2026-03-01T08:00:00Z"))
        val second = TestData.snapshot(mar1, heavy, ProfileSource.HEALTH_CONNECT, Instant.parse("2026-03-01T09:00:00Z"))
        env.db.profileDao().insertAll(listOf(second.toEntity(), first.toEntity()))
        assertThat(repository().profileFor(mar1)!!.id).isEqualTo(second.id)
    }

    @Test
    fun `observeCurrent follows saves and the pin setting`() = runTest {
        val repo = repository()
        repo.observeCurrent().test {
            assertThat(awaitItem()).isNull()
            repo.save(light, jan1)
            assertThat(awaitItem()!!.weightKg).isEqualTo(60.0)
            repo.addHealthConnectWeight(65.5, mar1)
            assertThat(awaitItem()!!.weightKg).isEqualTo(65.5)
            settings.update { it.copy(goal = it.goal.copy(pinned = PinnedInputs(pinWeight = true))) }
            assertThat(awaitItem()!!.weightKg).isEqualTo(60.0)
            cancelAndIgnoreRemainingEvents()
        }
        repo.observeSnapshots().test {
            assertThat(awaitItem()).hasSize(2)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
