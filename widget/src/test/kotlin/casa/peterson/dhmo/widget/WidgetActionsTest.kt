package casa.peterson.dhmo.widget

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import casa.peterson.dhmo.core.data.usecase.intake.LogIntakeUseCase
import casa.peterson.dhmo.core.model.Container
import casa.peterson.dhmo.core.model.ContainerIcon
import casa.peterson.dhmo.core.model.Source
import casa.peterson.dhmo.core.model.settings.ThemeMode
import casa.peterson.dhmo.core.testing.FakeContainerRepository
import casa.peterson.dhmo.core.testing.FakeIntakeRepository
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Logging from a coaster, the Undo that follows it, and the theme the widget draws with. */
@RunWith(AndroidJUnit4::class)
class WidgetActionsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = WidgetUndoStore(context)
    private val intake = FakeIntakeRepository()
    private val glass = Container(UUID.randomUUID(), "Glass", 250, ContainerIcon.GLASS, 0, Instant.EPOCH)
    private val bottle = Container(UUID.randomUUID(), "Bottle", 500, ContainerIcon.BOTTLE, 1, Instant.EPOCH)
    private val containers = FakeContainerRepository(listOf(glass, bottle))

    @Before
    fun clearStore() = store.clear()

    @Test
    fun `a coaster logs its own container from the widget`() = runTest {
        val entry = logContainer(containers, LogIntakeUseCase(intake), bottle.id.toString())
        assertThat(entry?.volumeMl).isEqualTo(500)
        assertThat(entry?.source).isEqualTo(Source.WIDGET)
    }

    @Test
    fun `a coaster for a deleted container, or a malformed id, logs nothing`() = runTest {
        val log = LogIntakeUseCase(intake)
        assertThat(logContainer(containers, log, UUID.randomUUID().toString())).isNull()
        assertThat(logContainer(containers, log, "not-a-uuid")).isNull()
        assertThat(logContainer(containers, log, null)).isNull()
        assertThat(intake.entries(LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1))).isEmpty()
    }

    @Test
    fun `undo is offered for ten seconds after a widget log`() {
        val id = UUID.randomUUID()
        val now = Instant.parse("2026-06-10T14:15:00Z")
        store.record(id, 250, now)
        assertThat(store.pending(now.plusSeconds(9))).isEqualTo(PendingUndo(id, 250))
        assertThat(store.pending(now.plusSeconds(10))).isNull()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a running widget sees the offer appear, then lapse when the window ends`() = runTest {
        val start = Instant.parse("2026-06-10T14:15:00Z")
        val id = UUID.randomUUID()
        // The clock follows the test's virtual time, so the offer's delay is the only thing that moves it.
        store.offers { start.plusMillis(testScheduler.currentTime) }.test {
            assertThat(awaitItem()).isNull()
            store.record(id, 250, start.plusMillis(testScheduler.currentTime))
            assertThat(awaitItem()).isEqualTo(PendingUndo(id, 250))
            assertThat(awaitItem()).isNull()
            assertThat(testScheduler.currentTime).isEqualTo(WidgetUndoStore.UNDO_WINDOW.toMillis())
        }
    }

    @Test
    fun `undoing ends the offer at once`() = runTest {
        val now = Instant.parse("2026-06-10T14:15:00Z")
        val id = UUID.randomUUID()
        store.offers { now }.test {
            assertThat(awaitItem()).isNull()
            store.record(id, 250, now)
            assertThat(awaitItem()).isEqualTo(PendingUndo(id, 250))
            store.clear()
            assertThat(awaitItem()).isNull()
        }
    }

    @Test
    fun `only the latest widget log can be undone`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val now = Instant.parse("2026-06-10T14:15:00Z")
        store.record(first, 250, now)
        store.record(second, 500, now.plusSeconds(2))
        assertThat(store.isLatest(first)).isFalse()
        assertThat(store.isLatest(second)).isTrue()
        store.clear()
        assertThat(store.isLatest(second)).isFalse()
        assertThat(store.pending(now)).isNull()
    }

    @Test
    fun `the widget follows the app's theme choice, and the system only when asked to`() {
        val night = Configuration().apply { uiMode = Configuration.UI_MODE_NIGHT_YES }
        val day = Configuration().apply { uiMode = Configuration.UI_MODE_NIGHT_NO }
        assertThat(resolveNight(ThemeMode.SYSTEM, night)).isTrue()
        assertThat(resolveNight(ThemeMode.SYSTEM, day)).isFalse()
        assertThat(resolveNight(ThemeMode.DARK, day)).isTrue()
        assertThat(resolveNight(ThemeMode.LIGHT, night)).isFalse()
    }
}
