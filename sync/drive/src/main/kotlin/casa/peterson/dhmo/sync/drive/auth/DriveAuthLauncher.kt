package casa.peterson.dhmo.sync.drive.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

/** Starts the Google sign-in and Drive authorisation flows, which need an activity-result launcher (§6.2). */
class DriveAuthLauncher internal constructor(private val onLaunch: () -> Unit) {
    fun launch() = onLaunch()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface DriveAuthEntryPoint {
    fun authManager(): GoogleAuthManager
}

/**
 * Remembers a launcher that signs in, asks for the Drive scope and reports the outcome. `onResult(true)` means the
 * account is signed in and Drive access is granted; `false` means the user backed out or this build has no client id.
 */
@Composable
fun rememberDriveAuthLauncher(onResult: (authorised: Boolean) -> Unit): DriveAuthLauncher {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth =
        remember(context) { EntryPointAccessors.fromApplication(context.applicationContext, DriveAuthEntryPoint::class.java).authManager() }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        scope.launch { onResult(auth.completeResolution(result.data) is DriveAuthResult.Authorized) }
    }
    return remember(auth, consent, onResult) {
        DriveAuthLauncher {
            scope.launch {
                val activity = context.findActivity()
                val email = if (activity == null) null else auth.signIn(activity)
                if (email == null) {
                    onResult(false)
                    return@launch
                }
                auth.rememberAccount(email)
                when (val result = auth.authorize()) {
                    is DriveAuthResult.Authorized -> onResult(true)

                    is DriveAuthResult.NeedsResolution -> consent.launch(
                        IntentSenderRequest.Builder(result.pendingIntent.intentSender).build(),
                    )

                    DriveAuthResult.Cancelled, DriveAuthResult.NotConfigured -> onResult(false)
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
