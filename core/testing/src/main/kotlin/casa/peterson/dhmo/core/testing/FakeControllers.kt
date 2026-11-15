package casa.peterson.dhmo.core.testing

import casa.peterson.dhmo.core.data.repository.DailyForecast
import casa.peterson.dhmo.core.data.repository.DriveBackupController
import casa.peterson.dhmo.core.data.repository.GeoPlace
import casa.peterson.dhmo.core.data.repository.HealthConnectController
import casa.peterson.dhmo.core.data.repository.ReminderScheduler
import casa.peterson.dhmo.core.data.repository.WeatherController
import casa.peterson.dhmo.core.data.repository.WeatherFailure
import casa.peterson.dhmo.core.data.repository.WeatherProvider
import casa.peterson.dhmo.core.data.repository.WeatherResult
import casa.peterson.dhmo.core.data.repository.WidgetUpdater
import casa.peterson.dhmo.core.model.RescheduleReason
import casa.peterson.dhmo.core.model.WeatherDay
import casa.peterson.dhmo.core.model.sync.DriveBackupState
import casa.peterson.dhmo.core.model.sync.HcAvailability
import casa.peterson.dhmo.core.model.sync.HcSetting
import casa.peterson.dhmo.core.model.sync.HcSyncState
import casa.peterson.dhmo.core.model.sync.RemoteBackup
import casa.peterson.dhmo.core.model.sync.RestoreMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Records every call so tests can assert on the reschedule reasons. */
class FakeReminderScheduler : ReminderScheduler {
    val reasons = mutableListOf<RescheduleReason>()
    var cancelCount = 0
        private set
    var snoozeCount = 0
        private set

    override suspend fun reschedule(reason: RescheduleReason) {
        reasons += reason
    }

    override suspend fun cancel() {
        cancelCount++
    }

    override suspend fun snooze() {
        snoozeCount++
    }
}

class FakeWidgetUpdater : WidgetUpdater {
    var updateCount = 0
        private set

    override suspend fun updateAll() {
        updateCount++
    }
}

class FakeHealthConnectController(
    var availability: HcAvailability = HcAvailability.UNAVAILABLE,
    initialState: HcSyncState = HcSyncState.Disabled,
) : HealthConnectController {
    val stateFlow = MutableStateFlow(initialState)
    override val state: Flow<HcSyncState> = stateFlow
    val syncRequests = mutableListOf<Boolean>()
    var backfillCount = 0
        private set
    var settingsChangedCount = 0
        private set

    override fun availability(): HcAvailability = availability

    override fun permissionsFor(setting: HcSetting): Set<String> = setOf("permission.${setting.name}")

    override suspend fun requestSync(immediate: Boolean) {
        syncRequests += immediate
    }

    override suspend fun enqueueBackfill() {
        backfillCount++
    }

    override suspend fun onSettingsChanged() {
        settingsChangedCount++
    }
}

class FakeDriveBackupController(initialState: DriveBackupState = DriveBackupState.NotConfigured) : DriveBackupController {
    val stateFlow = MutableStateFlow(initialState)
    override val state: Flow<DriveBackupState> = stateFlow
    var backups: List<RemoteBackup> = emptyList()
    var backUpCount = 0
        private set
    var disconnectCount = 0
        private set
    val restores = mutableListOf<Pair<RemoteBackup, RestoreMode>>()

    override suspend fun backUpNow() {
        backUpCount++
    }

    override suspend fun listBackups(): Result<List<RemoteBackup>> = Result.success(backups)

    override suspend fun restore(backup: RemoteBackup, mode: RestoreMode, passphrase: CharArray?): Result<Unit> {
        restores += backup to mode
        return Result.success(Unit)
    }

    override suspend fun onSettingsChanged() = Unit

    override suspend fun disconnect() {
        disconnectCount++
    }
}

class FakeWeatherController(var result: WeatherResult<WeatherDay> = WeatherResult.Failure(WeatherFailure.NO_DATA)) : WeatherController {
    var refreshIfStaleCount = 0
        private set
    var refreshNowCount = 0
        private set

    override suspend fun refreshIfStale() {
        refreshIfStaleCount++
    }

    override suspend fun refreshNow(): WeatherResult<WeatherDay> {
        refreshNowCount++
        return result
    }

    override suspend fun onSettingsChanged() = Unit
}

class FakeWeatherProvider(
    var forecast: WeatherResult<DailyForecast> = WeatherResult.Failure(WeatherFailure.NO_DATA),
    var places: WeatherResult<List<GeoPlace>> = WeatherResult.Success(emptyList()),
) : WeatherProvider {
    /** Every `(latE1, lonE1)` requested. */
    val forecastRequests = mutableListOf<Pair<Int, Int>>()

    override suspend fun todayForecast(latE1: Int, lonE1: Int): WeatherResult<DailyForecast> {
        forecastRequests += latE1 to lonE1
        return forecast
    }

    override suspend fun searchCity(query: String, languageTag: String): WeatherResult<List<GeoPlace>> = places
}
