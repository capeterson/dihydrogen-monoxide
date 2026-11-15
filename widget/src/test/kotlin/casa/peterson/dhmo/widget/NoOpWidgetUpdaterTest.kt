package casa.peterson.dhmo.widget

import kotlinx.coroutines.test.runTest
import org.junit.Test

class NoOpWidgetUpdaterTest {
    @Test
    fun `updating is a no-op`() = runTest { NoOpWidgetUpdater().updateAll() }
}
