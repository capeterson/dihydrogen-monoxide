package casa.peterson.dhmo.sync.healthconnect.engine

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The client only exists when Health Connect is installed, so it is created on demand and never injected directly. */
fun interface HealthConnectClientProvider {
    fun get(): HealthConnectClient
}

internal class DefaultHealthConnectClientProvider @Inject constructor(@param:ApplicationContext private val context: Context) :
    HealthConnectClientProvider {
    override fun get(): HealthConnectClient = HealthConnectClient.getOrCreate(context)
}
