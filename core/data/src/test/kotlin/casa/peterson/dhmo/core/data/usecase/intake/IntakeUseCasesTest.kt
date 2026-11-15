package casa.peterson.dhmo.core.data.usecase.intake

import casa.peterson.dhmo.core.model.Source
import casa.peterson.dhmo.core.testing.FakeIntakeRepository
import casa.peterson.dhmo.core.testing.MutableClock
import casa.peterson.dhmo.core.testing.TestData
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Test

class IntakeUseCasesTest {
    private val now = Instant.parse("2026-06-10T08:00:00Z")
    private val repository = FakeIntakeRepository(MutableClock(now))
    private val log = LogIntakeUseCase(repository)
    private val edit = EditIntakeUseCase(repository)
    private val delete = DeleteIntakeUseCase(repository)
    private val undo = UndoDeleteIntakeUseCase(repository)

    private suspend inline fun <reified T : Throwable> failureOf(block: () -> Unit): Throwable? =
        runCatching { block() }.exceptionOrNull().also { assertThat(it).isInstanceOf(T::class.java) }

    @Test
    fun `log accepts the limits and stores the entry`() = runTest {
        assertThat(log(1, Source.MANUAL).volumeMl).isEqualTo(1)
        assertThat(log(10_000, Source.WIDGET, note = "x".repeat(200)).note).hasLength(200)
        val entry = log(250, Source.NOTIFICATION, at = now.minusSeconds(60))
        assertThat(entry.instant).isEqualTo(now.minusSeconds(60))
        assertThat(repository.all).hasSize(3)
    }

    @Test
    fun `log rejects volumes outside 1 to 10000 and long notes`() = runTest {
        failureOf<IllegalArgumentException> { log(0, Source.MANUAL) }
        failureOf<IllegalArgumentException> { log(-5, Source.MANUAL) }
        failureOf<IllegalArgumentException> { log(10_001, Source.MANUAL) }
        failureOf<IllegalArgumentException> { log(250, Source.MANUAL, note = "x".repeat(201)) }
        assertThat(repository.all).isEmpty()
    }

    @Test
    fun `edit validates and updates`() = runTest {
        val entry = log(250, Source.MANUAL)
        val edited = edit(entry.id, 400, now.minusSeconds(120), "bigger")
        assertThat(edited.volumeMl).isEqualTo(400)
        assertThat(repository.get(entry.id)!!.note).isEqualTo("bigger")

        failureOf<IllegalArgumentException> { edit(entry.id, 0, now, null) }
        failureOf<IllegalArgumentException> { edit(entry.id, 10_001, now, null) }
        assertThat(repository.get(entry.id)!!.volumeMl).isEqualTo(400)
    }

    @Test
    fun `delete then undo`() = runTest {
        val entry = log(250, Source.MANUAL)
        delete(entry.id)
        assertThat(repository.get(entry.id)!!.deletedAt).isNotNull()
        undo(entry.id)
        assertThat(repository.get(entry.id)!!.deletedAt).isNull()
    }

    @Test
    fun `unknown and already deleted entries are not found`() = runTest {
        val missing = UUID.randomUUID()
        failureOf<EntryNotFoundException> { edit(missing, 250, now, null) }
        failureOf<EntryNotFoundException> { delete(missing) }
        failureOf<EntryNotFoundException> { undo(missing) }

        val entry = log(250, Source.MANUAL)
        delete(entry.id)
        failureOf<EntryNotFoundException> { delete(entry.id) }
        failureOf<EntryNotFoundException> { edit(entry.id, 100, now, null) }
    }

    @Test
    fun `imports are rejected until adopted`() = runTest {
        val import = TestData.importedEntry(now)
        repository.put(import)

        failureOf<ImportNotEditableException> { edit(import.id, 100, now, null) }
        failureOf<ImportNotEditableException> { delete(import.id) }
        failureOf<ImportNotEditableException> { undo(import.id) }
        assertThat(repository.get(import.id)).isEqualTo(import)
        assertThat(repository.deletedIds).isEmpty()

        repository.adopt(import.id)
        assertThat(edit(import.id, 100, now, null).volumeMl).isEqualTo(100)
        delete(import.id)
        undo(import.id)
        assertThat(repository.get(import.id)!!.deletedAt).isNull()
    }
}
