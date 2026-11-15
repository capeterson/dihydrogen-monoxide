package casa.peterson.dhmo.core.data.repository

import casa.peterson.dhmo.core.model.sync.HcAvailability
import casa.peterson.dhmo.core.model.sync.HcSetting
import casa.peterson.dhmo.core.model.sync.HcSyncState
import kotlinx.coroutines.flow.Flow

/** Implemented by `:sync:healthconnect` (Phase 5). */
interface HealthConnectController {
    val state: Flow<HcSyncState>

    fun availability(): HcAvailability

    fun permissionsFor(setting: HcSetting): Set<String>

    suspend fun requestSync(immediate: Boolean)

    suspend fun enqueueBackfill()

    suspend fun onSettingsChanged()
}
