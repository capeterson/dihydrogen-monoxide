package casa.peterson.dhmo.sync.healthconnect.ui

import androidx.health.connect.client.HealthConnectFeatures
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.data.repository.HealthConnectController
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.model.settings.HealthConnectSettings
import casa.peterson.dhmo.core.model.sync.HcAvailability
import casa.peterson.dhmo.core.model.sync.HcSetting
import casa.peterson.dhmo.core.model.sync.HcSyncState
import casa.peterson.dhmo.sync.healthconnect.HealthConnectSources
import casa.peterson.dhmo.sync.healthconnect.engine.HcPermissions
import casa.peterson.dhmo.sync.healthconnect.engine.HealthConnectClientProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class SourceRow(val packageName: String, val label: String, val included: Boolean)

internal data class HcUiState(
    val availability: HcAvailability = HcAvailability.UNAVAILABLE,
    val settings: HealthConnectSettings = HealthConnectSettings(),
    val sync: HcSyncState = HcSyncState.Disabled,
    val backgroundOffered: Boolean = false,
    val historyOffered: Boolean = false,
    val sources: List<SourceRow> = emptyList(),
    val permissionDenied: Boolean = false,
)

@HiltViewModel
internal class HealthConnectSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val controller: HealthConnectController,
    private val sources: HealthConnectSources,
    private val clients: HealthConnectClientProvider,
) : ViewModel() {
    private val denied = MutableStateFlow(false)
    private val features = MutableStateFlow(false to false)

    val state: StateFlow<HcUiState> = combine(settings.settings, controller.state, denied, features) { s, sync, wasDenied, (bg, history) ->
        HcUiState(
            availability = controller.availability(),
            settings = s.healthConnect,
            sync = sync,
            backgroundOffered = bg,
            historyOffered = history,
            sources = s.healthConnect.importSources.map { (pkg, included) ->
                SourceRow(pkg, sources.label(pkg), included)
            }.sortedBy { it.label },
            permissionDenied = wasDenied,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HcUiState())

    init {
        refreshFeatures()
    }

    fun permissionsFor(setting: HcSetting): Set<String> = controller.permissionsFor(setting)

    fun isOn(setting: HcSetting): Boolean = HcPermissions.isOn(state.value.settings, setting)

    /** Turning on needs the permission first (the screen asks and calls [onPermissionResult]); turning off is immediate. */
    fun turnOff(setting: HcSetting) {
        denied.value = false
        viewModelScope.launch {
            write(setting, false)
            if (setting == HcSetting.IMPORT_HYDRATION) sources.hideAllImports()
            controller.onSettingsChanged()
        }
    }

    fun onPermissionResult(setting: HcSetting, granted: Set<String>) {
        viewModelScope.launch {
            val ok = granted.containsAll(controller.permissionsFor(setting))
            denied.value = !ok
            write(setting, ok)
            if (ok && setting == HcSetting.WRITE_HYDRATION) controller.enqueueBackfill()
            controller.onSettingsChanged()
        }
    }

    fun syncNow() {
        viewModelScope.launch { controller.requestSync(immediate = true) }
    }

    fun setSourceIncluded(packageName: String, included: Boolean) {
        viewModelScope.launch { sources.setIncluded(packageName, included) }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun refreshFeatures() {
        viewModelScope.launch {
            if (controller.availability() != HcAvailability.AVAILABLE) return@launch
            try {
                val available = clients.get().features
                features.update {
                    (
                        available.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
                            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
                        ) to
                        (
                            available.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
                                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
                            )
                }
            } catch (_: RuntimeException) {
                // Older providers cannot report features; the two options stay hidden.
            }
        }
    }

    private suspend fun write(setting: HcSetting, on: Boolean) {
        settings.update { s ->
            val hc = s.healthConnect
            s.copy(
                healthConnect = when (setting) {
                    HcSetting.WRITE_HYDRATION -> hc.copy(writeHydration = on)
                    HcSetting.IMPORT_HYDRATION -> hc.copy(importHydration = on)
                    HcSetting.SYNC_WEIGHT -> hc.copy(syncWeight = on)
                    HcSetting.USE_EXERCISE -> hc.copy(useExercise = on)
                    HcSetting.READ_STEPS_AND_CALORIES -> hc.copy(readStepsAndCalories = on)
                    HcSetting.BACKGROUND_SYNC -> hc.copy(backgroundSync = on)
                    HcSetting.IMPORT_HISTORY -> hc.copy(importHistory = on)
                },
            )
        }
    }
}
