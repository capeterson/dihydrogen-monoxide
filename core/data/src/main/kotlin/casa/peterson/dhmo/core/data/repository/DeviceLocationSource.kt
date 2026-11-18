package casa.peterson.dhmo.core.data.repository

/** The device's approximate location, for weather. Implemented by `:core:weather`; screens only need these questions. */
interface DeviceLocationSource {
    /** False when Google Play services is missing, so device mode cannot work on this phone. */
    fun isSupported(): Boolean

    fun hasPermission(): Boolean

    /** Reads and caches a location rounded to 0.1 degrees. False when none could be read. */
    suspend fun refresh(): Boolean
}
