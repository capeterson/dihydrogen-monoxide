package casa.peterson.dhmo.wear

import android.content.ComponentName
import android.content.Context
import androidx.core.content.edit
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import casa.peterson.dhmo.core.wear.LogRequest
import casa.peterson.dhmo.core.wear.WearProtocol
import casa.peterson.dhmo.core.wear.WearState
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.time.Clock
import kotlinx.coroutines.tasks.await

internal class PrefsStateStore(context: Context) : StateStore {
    private val prefs = context.getSharedPreferences("wear_state", Context.MODE_PRIVATE)

    override fun read(): WearState? = prefs.getString(KEY, null)?.let { WearProtocol.decodeState(it.toByteArray()) }

    override fun write(state: WearState) = prefs.edit { putString(KEY, WearProtocol.encode(state).decodeToString()) }

    private companion object {
        const val KEY = "state"
    }
}

internal class DataLayerPhoneLink(private val context: Context) : PhoneLink {
    override suspend fun send(request: LogRequest): Boolean {
        val nodes = Wearable.getCapabilityClient(context)
            .getCapability(WearProtocol.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .await()
            .nodes
        val messages = Wearable.getMessageClient(context)
        val payload = WearProtocol.encode(request)
        return nodes.any { node -> runCatching { messages.sendMessage(node.id, WearProtocol.LOG, payload).await() }.isSuccess }
    }

    override suspend fun queue(request: LogRequest) {
        val item = PutDataMapRequest.create(WearProtocol.PENDING_PREFIX + request.id).apply {
            dataMap.putByteArray(WearProtocol.KEY_PAYLOAD, WearProtocol.encode(request))
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(item).await()
    }

    override suspend fun requestState() {
        val nodes = Wearable.getCapabilityClient(context)
            .getCapability(WearProtocol.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
            .await()
            .nodes
        val messages = Wearable.getMessageClient(context)
        nodes.forEach { runCatching { messages.sendMessage(it.id, WearProtocol.STATE_REQUEST, ByteArray(0)).await() } }
    }
}

internal class SystemSurfaceRefresher(private val context: Context, private val tile: Boolean = true) : SurfaceRefresher {
    override fun refresh() {
        if (tile) TileService.getUpdater(context).requestUpdate(WaterTileService::class.java)
        ComplicationDataSourceUpdateRequester.create(
            context,
            ComponentName(context, WaterComplicationService::class.java),
        ).requestUpdateAll()
    }
}

/** The watch has no dependency injection; these are created on demand from the application context. */
internal object WearGraph {
    fun store(context: Context): StateStore = PrefsStateStore(context.applicationContext)

    fun link(context: Context): PhoneLink = DataLayerPhoneLink(context.applicationContext)

    fun refresher(context: Context): SurfaceRefresher = SystemSurfaceRefresher(context.applicationContext)

    fun complicationRefresher(context: Context): SurfaceRefresher = SystemSurfaceRefresher(context.applicationContext, tile = false)

    fun logger(context: Context) = WaterLogger(store(context), link(context), refresher(context), Clock.systemUTC())
}
