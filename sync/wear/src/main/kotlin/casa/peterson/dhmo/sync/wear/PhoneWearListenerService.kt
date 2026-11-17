package casa.peterson.dhmo.sync.wear

import android.util.Log
import casa.peterson.dhmo.core.common.ApplicationScope
import casa.peterson.dhmo.core.wear.WearProtocol
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

/** Receives drinks from the watch: live messages, and data items the watch queued while the phone was unreachable. */
@AndroidEntryPoint
class PhoneWearListenerService : WearableListenerService() {
    @Inject lateinit var handler: WearLogHandler

    @Inject lateinit var stateSync: WearStateSync

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearProtocol.LOG -> scope.launch { handler.handle(event.data) }
            WearProtocol.STATE_REQUEST -> scope.launch { stateSync.republish() }
        }
    }

    /** Runs on the service's binder thread; data items are few, so they are handled before the buffer is released. */
    override fun onDataChanged(events: DataEventBuffer) {
        val pending = events.filter {
            it.type == DataEvent.TYPE_CHANGED &&
                it.dataItem.uri.path.orEmpty().startsWith(WearProtocol.PENDING_PREFIX)
        }
            .map { it.dataItem.uri to DataMapItem.fromDataItem(it.dataItem).dataMap.getByteArray(WearProtocol.KEY_PAYLOAD) }
        if (pending.isEmpty()) return
        runBlocking {
            val dataClient = Wearable.getDataClient(this@PhoneWearListenerService)
            pending.forEach { (uri, payload) ->
                if (payload != null) handler.handle(payload)
                // Delete even when it was a duplicate or malformed, so it is not delivered again.
                runCatching { dataClient.deleteDataItems(uri).await() }.onFailure { Log.w(TAG, "Could not delete $uri", it) }
            }
        }
    }

    private companion object {
        const val TAG = "PhoneWearListener"
    }
}
