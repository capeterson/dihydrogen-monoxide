package casa.peterson.dhmo.widget

import casa.peterson.dhmo.core.data.usecase.intake.LogIntakeUseCase
import casa.peterson.dhmo.core.model.Container
import casa.peterson.dhmo.core.model.ContainerIcon
import casa.peterson.dhmo.core.model.Source
import casa.peterson.dhmo.core.testing.FakeContainerRepository
import casa.peterson.dhmo.core.testing.FakeIntakeRepository
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LogContainerActionTest {
    private val glass = Container(UUID.randomUUID(), "Glass", 250, ContainerIcon.GLASS, 0, Instant.EPOCH)
    private val bottle = Container(UUID.randomUUID(), "Bottle", 500, ContainerIcon.BOTTLE, 1, Instant.EPOCH)
    private val containers = FakeContainerRepository(listOf(glass, bottle))
    private val intake = FakeIntakeRepository()
    private val logIntake = LogIntakeUseCase(intake)

    @Test
    fun `the tapped container is logged with its own volume from the widget`() = runTest {
        assertThat(logContainer(containers, logIntake, bottle.id.toString())).isTrue()
        val logged = intake.all.single()
        assertThat(logged.volumeMl).isEqualTo(500)
        assertThat(logged.source).isEqualTo(Source.WIDGET)
    }

    @Test
    fun `the default container is not logged when another is tapped`() = runTest {
        logContainer(containers, logIntake, bottle.id.toString())
        assertThat(intake.all.map { it.volumeMl }).doesNotContain(250)
    }

    @Test
    fun `a container deleted since the widget was drawn logs nothing`() = runTest {
        containers.delete(glass.id)
        assertThat(logContainer(containers, logIntake, glass.id.toString())).isFalse()
        assertThat(intake.all).isEmpty()
    }

    @Test
    fun `a missing or malformed id logs nothing`() = runTest {
        assertThat(logContainer(containers, logIntake, null)).isFalse()
        assertThat(logContainer(containers, logIntake, "not-a-uuid")).isFalse()
        assertThat(intake.all).isEmpty()
    }
}
