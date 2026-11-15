package casa.peterson.dhmo.sync.drive.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Starts the Google sign-in and Drive authorisation flows, which need an activity-result launcher (§6.2). */
class DriveAuthLauncher internal constructor(private val onLaunch: () -> Unit) {
    fun launch() = onLaunch()
}

/**
 * A stub until T-DR1: launching reports "not authorised" immediately. `:app` hands the real launcher to the
 * Drive screens in Phase 4.
 */
@Composable
fun rememberDriveAuthLauncher(onResult: (authorised: Boolean) -> Unit): DriveAuthLauncher =
    remember(onResult) { DriveAuthLauncher { onResult(false) } }
