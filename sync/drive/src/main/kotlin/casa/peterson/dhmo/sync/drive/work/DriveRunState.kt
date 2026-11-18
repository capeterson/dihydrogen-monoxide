package casa.peterson.dhmo.sync.drive.work

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

/** Whether a backup is running right now, shared by the runner (which sets it) and the controller (which shows it). */
@Singleton
internal class DriveRunState @Inject constructor() {
    val running = MutableStateFlow(false)
}
