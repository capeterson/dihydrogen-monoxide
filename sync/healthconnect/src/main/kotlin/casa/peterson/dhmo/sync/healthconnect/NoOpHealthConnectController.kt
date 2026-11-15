package casa.peterson.dhmo.sync.healthconnect

import casa.peterson.dhmo.core.data.repository.HealthConnectController
import casa.peterson.dhmo.core.model.sync.HcAvailability
import casa.peterson.dhmo.core.model.sync.HcSetting
import casa.peterson.dhmo.core.model.sync.HcSyncState
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Replaced by `HealthConnectControllerImpl` in T-HC2. */
internal class NoOpHealthConnectController
@Inject
constructor() : HealthConnectController {
    override val state: Flow<HcSyncState> = flowOf(HcSyncState.Unavailable)

    override fun availability(): HcAvailability = HcAvailability.UNAVAILABLE

    override fun permissionsFor(setting: HcSetting): Set<String> = emptySet()

    override suspend fun requestSync(immediate: Boolean) = Unit

    override suspend fun enqueueBackfill() = Unit

    override suspend fun onSettingsChanged() = Unit
}
