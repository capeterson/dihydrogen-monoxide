package casa.peterson.dhmo.wear

import casa.peterson.dhmo.core.wear.WearProtocol
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/** Stores each state the phone publishes and redraws the tile and complications. */
class StateListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        val latest = events
            .filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == WearProtocol.STATE }
            .mapNotNull { DataMapItem.fromDataItem(it.dataItem).dataMap.getByteArray(WearProtocol.KEY_PAYLOAD) }
            .mapNotNull(WearProtocol::decodeState)
            .maxByOrNull { it.updatedAtMillis }
            ?: return
        WearGraph.store(this).write(latest)
        WearGraph.refresher(this).refresh()
    }
}
