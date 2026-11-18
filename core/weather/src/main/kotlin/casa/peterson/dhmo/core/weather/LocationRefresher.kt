package casa.peterson.dhmo.core.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import casa.peterson.dhmo.core.data.repository.DeviceLocationSource
import casa.peterson.dhmo.core.data.repository.DeviceStateRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.model.settings.CachedLocation
import casa.peterson.dhmo.core.model.settings.WeatherMode
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Reads a coarse location, only while the app is in the foreground, and caches it rounded to 0.1 degrees (§12.4).
 * Workers never call the location API; they read this cache.
 */
@Singleton
class LocationRefresher @Inject internal constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val deviceState: DeviceStateRepository,
    private val clock: Clock,
) : DeviceLocationSource {
    override fun isSupported(): Boolean = isPlayServicesAvailable()

    override suspend fun refresh(): Boolean = refreshNow()

    fun isPlayServicesAvailable(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Refreshes the cache when the mode is device location, permission is held and the cache is over six hours old. */
    suspend fun refreshIfForeground(): Boolean {
        if (settings.current().weather.mode != WeatherMode.DEVICE_LOCATION) return false
        if (!hasPermission() || !isPlayServicesAvailable()) return false
        val cached = deviceState.current().cachedLocation
        if (cached != null && clock.millis() - cached.obtainedAtMs < STALE_AFTER_MS) return false
        return refreshNow()
    }

    @Suppress("MissingPermission", "TooGenericExceptionCaught")
    suspend fun refreshNow(): Boolean {
        if (!hasPermission() || !isPlayServicesAvailable()) return false
        val client = LocationServices.getFusedLocationProviderClient(context)
        val token = CancellationTokenSource()
        val location = try {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, token.token).await()
                ?: client.lastLocation.await()
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            null
        } ?: return false
        val rounded = CachedLocation(
            latE1 = LocationRounder.toE1(location.latitude),
            lonE1 = LocationRounder.toE1(location.longitude),
            obtainedAtMs = clock.millis(),
        )
        deviceState.update { it.copy(cachedLocation = rounded) }
        return true
    }

    private companion object {
        const val STALE_AFTER_MS = 6L * 60 * 60 * 1000
    }
}
