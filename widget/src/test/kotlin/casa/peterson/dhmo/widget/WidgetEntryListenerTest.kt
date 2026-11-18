package casa.peterson.dhmo.widget

import casa.peterson.dhmo.core.model.ChangeOrigin
import casa.peterson.dhmo.core.model.EntryChange
import casa.peterson.dhmo.core.testing.FakeWidgetUpdater
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Test

class WidgetEntryListenerTest {
    @Test
    fun `every entry change refreshes the widgets`() = runTest {
        val updater = FakeWidgetUpdater()
        val listener = WidgetEntryListener(updater)
        listener.onEntriesChanged(EntryChange(setOf(LocalDate.of(2026, 11, 17)), ChangeOrigin.LOCAL))
        assertThat(updater.updateCount).isEqualTo(1)
    }
}
