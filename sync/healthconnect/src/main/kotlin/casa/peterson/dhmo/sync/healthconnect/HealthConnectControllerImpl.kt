package casa.peterson.dhmo.sync.healthconnect

import android.content.Context
import android.os.RemoteException
import androidx.health.connect.client.HealthConnectClient
import casa.peterson.dhmo.core.data.repository.HealthConnectController
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.database.dao.HcSyncDao
import casa.peterson.dhmo.core.database.dao.IntakeDao
import casa.peterson.dhmo.core.database.entity.HcOp
import casa.peterson.dhmo.core.database.entity.HcPendingOpEntity
import casa.peterson.dhmo.core.model.Source
import casa.peterson.dhmo.core.model.settings.HealthConnectSettings
import casa.peterson.dhmo.core.model.sync.HcAvailability
import casa.peterson.dhmo.core.model.sync.HcError
import casa.peterson.dhmo.core.model.sync.HcSetting
import casa.peterson.dhmo.core.model.sync.HcSyncState
import casa.peterson.dhmo.sync.healthconnect.engine.HcPermissions
import casa.peterson.dhmo.sync.healthconnect.engine.HealthConnectClientProvider
import casa.peterson.dhmo.sync.healthconnect.engine.HealthConnectSyncEngine
import casa.peterson.dhmo.sync.healthconnect.work.HcRunResult
import casa.peterson.dhmo.sync.healthconnect.work.HealthConnectWorkScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/** The Health Connect state machine of TECHNICAL_PLAN §10.4, plus the triggers that start a sync. */
@Singleton
internal class HealthConnectControllerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val hcDao: HcSyncDao,
    private val intakeDao: IntakeDao,
    private val engine: HealthConnectSyncEngine,
    private val clients: HealthConnectClientProvider,
    private val scheduler: HealthConnectWorkScheduler,
    private val clock: Clock,
) : HealthConnectController {
    private sealed interface Run {
        data object None : Run

        data object Syncing : Run

        data class Failed(val reason: HcError) : Run

        data class NeedsPermission(val missing: Set<String>) : Run
    }

    private val run = MutableStateFlow<Run>(Run.None)
    private val lastSync = MutableStateFlow<Instant?>(null)

    override val state: Flow<HcSyncState> = combine(settings.settings, hcDao.observePendingCount(), run, lastSync) { s, pending, r, last ->
        when (val availability = availability()) {
            HcAvailability.UNAVAILABLE -> HcSyncState.Unavailable

            HcAvailability.PROVIDER_UPDATE_REQUIRED -> HcSyncState.ProviderUpdateRequired

            HcAvailability.AVAILABLE -> when {
                !HcPermissions.anyOn(s.healthConnect) -> HcSyncState.Disabled
                r is Run.Syncing -> HcSyncState.Syncing
                r is Run.NeedsPermission -> HcSyncState.NeedsPermission(r.missing)
                r is Run.Failed -> HcSyncState.Error(r.reason, last)
                else -> HcSyncState.Idle(last, pending).also { check(availability == HcAvailability.AVAILABLE) }
            }
        }
    }

    override fun availability(): HcAvailability = when (HealthConnectClient.getSdkStatus(context, PROVIDER_PACKAGE)) {
        HealthConnectClient.SDK_AVAILABLE -> HcAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcAvailability.PROVIDER_UPDATE_REQUIRED
        else -> HcAvailability.UNAVAILABLE
    }

    override fun permissionsFor(setting: HcSetting): Set<String> = HcPermissions.forSetting(setting)

    override suspend fun requestSync(immediate: Boolean) {
        if (availability() != HcAvailability.AVAILABLE || !HcPermissions.anyOn(settings.current().healthConnect)) return
        scheduler.enqueueSync(if (immediate) 0L else DEBOUNCE_SECONDS)
    }

    /** Switching writes on for the first time (or again): every live entry that did not come from Health Connect. */
    override suspend fun enqueueBackfill() {
        val now = clock.instant()
        intakeDao.getAllIncludingDeleted()
            .filter { it.deletedAt == null && it.source != Source.HEALTH_CONNECT_IMPORT }
            .forEach { hcDao.enqueue(HcPendingOpEntity(entryId = it.id, op = HcOp.UPSERT, enqueuedAt = now)) }
        requestSync(immediate = true)
    }

    override suspend fun onSettingsChanged() {
        val hc = settings.current().healthConnect
        if (availability() != HcAvailability.AVAILABLE || !HcPermissions.anyOn(hc)) {
            scheduler.cancelAll()
            if (!HcPermissions.anyOn(hc)) {
                // Everything is off: keep the stored record ids, drop the queue and the token (§10.4).
                hcDao.clearOps()
                hcDao.deleteState(HealthConnectSyncEngine.TOKEN)
                hcDao.deleteState(HealthConnectSyncEngine.TOKEN_TYPES)
                run.value = Run.None
            }
            return
        }
        if (hc.backgroundSync) scheduler.ensurePeriodic() else scheduler.cancelPeriodic()
        scheduler.enqueueSync(0L)
    }

    /** Called by the workers. Maps failures to a state and to the worker's retry decision (§10.4). */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun runSync(): HcRunResult {
        if (availability() != HcAvailability.AVAILABLE) return HcRunResult.FAILURE
        if (lastSync.value == null) lastSync.value = engine.lastSyncAt()
        run.value = Run.Syncing
        return try {
            val report = engine.sync()
            lastSync.value = report.syncedAt
            run.value = if (report.missingPermissions.isEmpty()) Run.None else Run.NeedsPermission(report.missingPermissions)
            HcRunResult.SUCCESS
        } catch (e: CancellationException) {
            run.value = Run.None
            throw e
        } catch (e: SecurityException) {
            run.value = Run.NeedsPermission(missingNow())
            HcRunResult.FAILURE
        } catch (e: Exception) {
            val reason = when {
                e is IOException || e is RemoteException || e is IllegalStateException ->
                    if (e.message?.contains("rate", ignoreCase = true) == true) HcError.RATE_LIMITED else HcError.IO

                else -> HcError.UNKNOWN
            }
            run.value = Run.Failed(reason)
            if (reason == HcError.UNKNOWN) HcRunResult.FAILURE else HcRunResult.RETRY
        }
    }

    private suspend fun missingNow(): Set<String> = runCatching {
        HcPermissions.missing(settings.current().healthConnect, clients.get().permissionController.getGrantedPermissions())
    }.getOrDefault(emptySet())

    companion object {
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
        private const val DEBOUNCE_SECONDS = 5L
    }
}

internal fun HealthConnectSettings.anyOn(): Boolean = HcPermissions.anyOn(this)
