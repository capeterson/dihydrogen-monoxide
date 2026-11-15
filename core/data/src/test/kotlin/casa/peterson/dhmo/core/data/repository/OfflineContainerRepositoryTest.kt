package casa.peterson.dhmo.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.data.RoomTestEnvironment
import casa.peterson.dhmo.core.model.Container
import casa.peterson.dhmo.core.model.ContainerIcon
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.core.testing.MutableClock
import casa.peterson.dhmo.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineContainerRepositoryTest {
    @get:Rule val env = RoomTestEnvironment()

    private val clock = MutableClock(Instant.parse("2026-06-10T08:00:00Z"))

    private fun repository() = OfflineContainerRepository(env.db.containerDao(), env.transactions, clock)

    @Test
    fun `seeds 250 500 750 ml for millilitres`() = runTest {
        val repo = repository()
        repo.seedDefaultsIfEmpty(VolumeUnit.ML)

        val all = repo.getAll()
        assertThat(all.map { Triple(it.id, it.name, it.volumeMl) }).containsExactly(
            Triple(TestData.GLASS_ID, "Glass", 250),
            Triple(TestData.BOTTLE_ID, "Bottle", 500),
            Triple(TestData.LARGE_BOTTLE_ID, "Large bottle", 750),
        ).inOrder()
        assertThat(all.map { it.sortOrder }).containsExactly(0, 1, 2).inOrder()
        assertThat(all.map { it.icon }).containsExactly(ContainerIcon.GLASS, ContainerIcon.BOTTLE, ContainerIcon.LARGE_BOTTLE).inOrder()
        assertThat(all).isEqualTo(TestData.defaultContainers(VolumeUnit.ML, clock.instant()))
    }

    @Test
    fun `seeds 8 16 24 fl oz as 237 473 710 ml for ounces`() = runTest {
        val repo = repository()
        repo.seedDefaultsIfEmpty(VolumeUnit.US_FL_OZ)
        assertThat(repo.getAll().map { it.volumeMl }).containsExactly(237, 473, 710).inOrder()
    }

    @Test
    fun `imperial ounces seed like US ounces`() = runTest {
        val repo = repository()
        repo.seedDefaultsIfEmpty(VolumeUnit.IMP_FL_OZ)
        assertThat(repo.getAll().map { it.volumeMl }).containsExactly(237, 473, 710).inOrder()
    }

    @Test
    fun `seeding twice or after the user deleted everything does not reseed`() = runTest {
        val repo = repository()
        repo.seedDefaultsIfEmpty(VolumeUnit.ML)
        repo.seedDefaultsIfEmpty(VolumeUnit.US_FL_OZ)
        assertThat(repo.getAll().map { it.volumeMl }).containsExactly(250, 500, 750).inOrder()

        repo.getAll().forEach { repo.delete(it.id) }
        repo.seedDefaultsIfEmpty(VolumeUnit.ML)
        assertThat(repo.getAll()).isEmpty()
    }

    @Test
    fun `reorder renumbers the sort order`() = runTest {
        val repo = repository()
        repo.seedDefaultsIfEmpty(VolumeUnit.ML)
        clock.advanceBy(Duration.ofMinutes(1))

        repo.reorder(listOf(TestData.LARGE_BOTTLE_ID, TestData.GLASS_ID, TestData.BOTTLE_ID))

        val all = repo.getAll()
        assertThat(all.map { it.id }).containsExactly(TestData.LARGE_BOTTLE_ID, TestData.GLASS_ID, TestData.BOTTLE_ID).inOrder()
        assertThat(all.map { it.sortOrder }).containsExactly(0, 1, 2).inOrder()
        assertThat(all.first().updatedAt).isEqualTo(clock.instant())
    }

    @Test
    fun `reorder keeps containers that were not listed after the listed ones`() = runTest {
        val repo = repository()
        repo.seedDefaultsIfEmpty(VolumeUnit.ML)
        repo.reorder(listOf(TestData.BOTTLE_ID))
        assertThat(repo.getAll().map { it.id })
            .containsExactly(TestData.BOTTLE_ID, TestData.GLASS_ID, TestData.LARGE_BOTTLE_ID).inOrder()
    }

    @Test
    fun `upsert and delete`() = runTest {
        val repo = repository()
        val mug = Container(UUID.randomUUID(), "Mug", 300, ContainerIcon.MUG, 5, Instant.EPOCH)
        repo.upsert(mug)
        assertThat(repo.getAll().single().updatedAt).isEqualTo(clock.instant())

        repo.upsert(mug.copy(name = "Big mug", volumeMl = 400))
        assertThat(repo.getAll().single().name).isEqualTo("Big mug")

        repo.delete(mug.id)
        assertThat(repo.getAll()).isEmpty()
    }
}
