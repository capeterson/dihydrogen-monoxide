package casa.peterson.dhmo.sync.drive

import app.cash.turbine.test
import casa.peterson.dhmo.core.model.sync.DriveBackupState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NoOpDriveBackupControllerTest {
    @Test
    fun `is not configured and lists nothing`() = runTest {
        val controller = NoOpDriveBackupController()
        controller.state.test {
            assertThat(awaitItem()).isEqualTo(DriveBackupState.NotConfigured)
            awaitComplete()
        }
        assertThat(controller.listBackups().getOrThrow()).isEmpty()
        controller.backUpNow()
        controller.onSettingsChanged()
        controller.disconnect()
    }
}
