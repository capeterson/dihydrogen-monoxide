package casa.peterson.dhmo.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import casa.peterson.dhmo.core.wear.WearState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The complication's tap action: logs the default (first) container. */
class LogDefaultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val volume = WearGraph.store(context).read()?.defaultContainer?.volumeMl ?: WearState.DEFAULT_ML
                WearGraph.logger(context).log(volume)
            } finally {
                pending.finish()
            }
        }
    }
}
