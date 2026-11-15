package casa.peterson.dhmo.feature.today

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import casa.peterson.dhmo.core.data.usecase.intake.EditIntakeUseCase
import casa.peterson.dhmo.core.model.ContainerIcon
import casa.peterson.dhmo.core.testing.FakeContainerRepository
import casa.peterson.dhmo.core.testing.FakeIntakeRepository
import casa.peterson.dhmo.core.testing.FakeSettingsRepository
import casa.peterson.dhmo.core.testing.MainDispatcherRule
import casa.peterson.dhmo.core.testing.MutableClock
import casa.peterson.dhmo.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class EditAndContainersViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val clock = MutableClock(Instant.parse("2026-06-10T12:00:00Z"))

    @Test
    fun editSavesAmountTimeAndNote() = runTest {
        val intake = FakeIntakeRepository(clock)
        val original = TestData.entry(clock.instant())
        intake.put(original)
        val vm = EditEntryViewModel(SavedStateHandle(mapOf("entryId" to original.id.toString())), intake, EditIntakeUseCase(intake))
        val editedAt = clock.instant().plusSeconds(60)
        vm.completedEvents.test {
            vm.save(500, editedAt, "after lunch")
            awaitItem()
            assertThat(intake.get(original.id)?.volumeMl).isEqualTo(500)
            assertThat(intake.get(original.id)?.instant).isEqualTo(editedAt)
            assertThat(intake.get(original.id)?.note).isEqualTo("after lunch")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun containersValidateCreateAndReorder() = runTest {
        val repository = FakeContainerRepository()
        val vm = ContainersViewModel(repository, FakeSettingsRepository(), clock)
        assertThat(vm.save("", 250, ContainerIcon.GLASS)).isFalse()
        assertThat(vm.save("Cup", 250, ContainerIcon.CUP)).isTrue()
        assertThat(vm.save("Bottle", 500, ContainerIcon.BOTTLE)).isTrue()
        val entries = repository.getAll()
        vm.move(entries[1].id, -1)
        assertThat(repository.getAll().map { it.name }).containsExactly("Bottle", "Cup").inOrder()
    }
}
