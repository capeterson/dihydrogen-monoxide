package casa.peterson.dhmo.core.reminders

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Runs [block] off the main thread under `goAsync()`, finishing within the broadcast time limit (§8.6). */
internal fun BroadcastReceiver.runAsync(scope: CoroutineScope, block: suspend () -> Unit) {
    val pending = goAsync()
    scope.launch {
        try {
            withTimeoutOrNull(RECEIVER_TIMEOUT_MS) { block() }
        } finally {
            pending.finish()
        }
    }
}

private const val RECEIVER_TIMEOUT_MS = 9_000L
