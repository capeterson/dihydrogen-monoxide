package casa.peterson.dhmo.sync.healthconnect

import app.cash.turbine.test
import casa.peterson.dhmo.core.model.sync.HcAvailability
import casa.peterson.dhmo.core.model.sync.HcSetting
import casa.peterson.dhmo.core.model.sync.HcSyncState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NoOpHealthConnectControllerTest {
    @Test
    fun `reports unavailable until phase 5`() = runTest {
        val controller = NoOpHealthConnectController()
        assertThat(controller.availability()).isEqualTo(HcAvailability.UNAVAILABLE)
        assertThat(controller.permissionsFor(HcSetting.WRITE_HYDRATION)).isEmpty()
        controller.state.test {
            assertThat(awaitItem()).isEqualTo(HcSyncState.Unavailable)
            awaitComplete()
        }
        controller.requestSync(immediate = true)
        controller.enqueueBackfill()
        controller.onSettingsChanged()
    }
}
