package casa.peterson.dhmo.core.testing

import casa.peterson.dhmo.core.backup.BackupDocument
import casa.peterson.dhmo.core.data.repository.ActivityRepository
import casa.peterson.dhmo.core.data.repository.BackupRepository
import casa.peterson.dhmo.core.data.repository.ContainerRepository
import casa.peterson.dhmo.core.data.repository.DeviceStateRepository
import casa.peterson.dhmo.core.data.repository.GoalRepository
import casa.peterson.dhmo.core.data.repository.ProfileRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.repository.WeatherRepository
import casa.peterson.dhmo.core.model.ActivityAdjustment
import casa.peterson.dhmo.core.model.ActivitySource
import casa.peterson.dhmo.core.model.Container
import casa.peterson.dhmo.core.model.DailyGoal
import casa.peterson.dhmo.core.model.DayOverride
import casa.peterson.dhmo.core.model.Intensity
import casa.peterson.dhmo.core.model.Profile
import casa.peterson.dhmo.core.model.ProfileSnapshot
import casa.peterson.dhmo.core.model.ProfileSource
import casa.peterson.dhmo.core.model.WeatherDay
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.settings.DeviceState
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import casa.peterson.dhmo.core.model.sync.RestoreMode
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state

    override suspend fun current(): AppSettings = state.value

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

class FakeDeviceStateRepository(initial: DeviceState = DeviceState()) : DeviceStateRepository {
    private val flow = MutableStateFlow(initial)
    override val state: Flow<DeviceState> = flow

    override suspend fun current(): DeviceState = flow.value

    override suspend fun update(transform: (DeviceState) -> DeviceState) {
        flow.value = transform(flow.value)
    }
}

class FakeContainerRepository(initial: List<Container> = emptyList()) : ContainerRepository {
    private val rows = MutableStateFlow(initial.sortedBy { it.sortOrder })

    /** The unit passed to the last [seedDefaultsIfEmpty] call, if any. */
    var seededWith: VolumeUnit? = null
        private set

    override fun observeAll(): Flow<List<Container>> = rows

    override suspend fun getAll(): List<Container> = rows.value

    override suspend fun upsert(container: Container) {
        rows.value = (rows.value.filter { it.id != container.id } + container).sortedBy { it.sortOrder }
    }

    override suspend fun delete(id: UUID) {
        rows.value = rows.value.filter { it.id != id }
    }

    override suspend fun reorder(idsInOrder: List<UUID>) {
        val byId = rows.value.associateBy { it.id }
        val listed = idsInOrder.mapNotNull { byId[it] }
        val rest = rows.value.filter { it.id !in idsInOrder }
        rows.value = (listed + rest).mapIndexed { index, container -> container.copy(sortOrder = index) }
    }

    override suspend fun seedDefaultsIfEmpty(units: VolumeUnit) {
        seededWith = units
        if (rows.value.isEmpty()) rows.value = TestData.defaultContainers(units)
    }
}

class FakeProfileRepository(initial: List<ProfileSnapshot> = emptyList(), private val clock: Clock = MutableClock(Instant.EPOCH)) :
    ProfileRepository {
    private val rows = MutableStateFlow(initial)

    override fun observeCurrent(): Flow<Profile?> = rows.map { it.maxWithOrNull(order)?.profile }

    override fun observeSnapshots(): Flow<List<ProfileSnapshot>> = rows

    override suspend fun profileFor(day: LocalDate): ProfileSnapshot? =
        rows.value.filter { it.effectiveFrom <= day }.maxWithOrNull(order) ?: rows.value.minWithOrNull(order)

    override suspend fun save(profile: Profile, effectiveFrom: LocalDate) {
        val kept = rows.value.filterNot { it.effectiveFrom == effectiveFrom && it.source == ProfileSource.MANUAL }
        rows.value = kept + ProfileSnapshot(UUID.randomUUID(), effectiveFrom, profile, ProfileSource.MANUAL, clock.instant())
    }

    override suspend fun addHealthConnectWeight(weightKg: Double, effectiveFrom: LocalDate) {
        val latest = rows.value.maxWithOrNull(order) ?: return
        rows.value = rows.value +
            latest.copy(
                id = UUID.randomUUID(),
                effectiveFrom = effectiveFrom,
                profile = latest.profile.copy(weightKg = weightKg),
                source = ProfileSource.HEALTH_CONNECT,
                createdAt = clock.instant(),
            )
    }

    private val order = compareBy<ProfileSnapshot>({ it.effectiveFrom }, { it.createdAt })
}

class FakeGoalRepository(private val clock: Clock = MutableClock(Instant.EPOCH)) : GoalRepository {
    private val goals = MutableStateFlow<Map<LocalDate, DailyGoal>>(emptyMap())
    private val overrides = MutableStateFlow<Map<LocalDate, DayOverride>>(emptyMap())

    /** Every goal passed to [saveSnapshot], in order. */
    val savedSnapshots = mutableListOf<DailyGoal>()

    override fun observeGoal(day: LocalDate): Flow<DailyGoal?> = goals.map { it[day] }

    override fun observeGoals(from: LocalDate, to: LocalDate): Flow<List<DailyGoal>> =
        goals.map { map -> map.values.filter { it.date in from..to }.sortedBy { it.date } }

    override suspend fun goals(from: LocalDate, to: LocalDate): List<DailyGoal> = observeGoals(from, to).first()

    override suspend fun goal(day: LocalDate): DailyGoal? = goals.value[day]

    override suspend fun saveSnapshot(goal: DailyGoal) {
        savedSnapshots += goal
        goals.value = goals.value + (goal.date to goal)
    }

    override fun observeDayOverride(day: LocalDate): Flow<DayOverride?> = overrides.map { it[day] }

    override suspend fun setDayOverride(day: LocalDate, targetMl: Int, reason: String?) {
        overrides.value = overrides.value + (day to DayOverride(day, targetMl, reason, clock.instant()))
    }

    override suspend fun clearDayOverride(day: LocalDate) {
        overrides.value = overrides.value - day
    }
}

class FakeActivityRepository(private val clock: Clock = MutableClock(Instant.EPOCH)) : ActivityRepository {
    private val rows = MutableStateFlow<List<ActivityAdjustment>>(emptyList())

    /** Seeds a row, for example one from Health Connect. */
    fun put(adjustment: ActivityAdjustment) {
        rows.value = rows.value.filterNot { it.source == adjustment.source && it.sourceRecordId == adjustment.sourceRecordId } + adjustment
    }

    override fun observeDay(day: LocalDate): Flow<List<ActivityAdjustment>> = rows.map { list -> list.filter { it.date == day } }

    override suspend fun forDay(day: LocalDate): List<ActivityAdjustment> = rows.value.filter { it.date == day }

    override suspend fun minutesPerDay(from: LocalDate, to: LocalDate): Map<LocalDate, Int> =
        rows.value.filter { it.date in from..to }.groupBy { it.date }.mapValues { (_, list) -> list.sumOf { it.minutes } }

    override suspend fun addPlanned(day: LocalDate, minutes: Int, intensity: Intensity) {
        put(
            ActivityAdjustment(
                source = ActivitySource.MANUAL_PLANNED,
                sourceRecordId = "manual-${UUID.randomUUID()}",
                date = day,
                minutes = minutes,
                intensity = intensity,
                extraMl = minutes * ML_PER_MINUTE,
                exerciseType = null,
                updatedAt = clock.instant(),
            ),
        )
    }

    override suspend fun removePlanned(sourceRecordId: String) {
        rows.value = rows.value.filterNot { it.source == ActivitySource.MANUAL_PLANNED && it.sourceRecordId == sourceRecordId }
    }

    private companion object {
        /** The fake does not model per-intensity rates. */
        const val ML_PER_MINUTE = 12
    }
}

class FakeWeatherRepository : WeatherRepository {
    private val rows = MutableStateFlow<Map<LocalDate, WeatherDay>>(emptyMap())

    override fun observeDay(day: LocalDate): Flow<WeatherDay?> = rows.map { it[day] }

    override suspend fun forDay(day: LocalDate): WeatherDay? = rows.value[day]

    override suspend fun range(from: LocalDate, to: LocalDate): List<WeatherDay> =
        rows.value.values.filter { it.date in from..to }.sortedBy { it.date }

    override suspend fun save(day: WeatherDay) {
        rows.value = rows.value + (day.date to day)
    }
}

class FakeBackupRepository(var document: BackupDocument? = null) : BackupRepository {
    /** Every `(document, mode)` passed to [restore]. */
    val restores = mutableListOf<Pair<BackupDocument, RestoreMode>>()
    var snapshotFile: File? = null

    override suspend fun exportDocument(): BackupDocument = checkNotNull(document) { "Set FakeBackupRepository.document first" }

    override suspend fun restore(doc: BackupDocument, mode: RestoreMode) {
        restores += doc to mode
    }

    var undoCount = 0

    override suspend fun undoLastRestore(): Boolean {
        if (snapshotFile == null) return false
        undoCount++
        snapshotFile = null
        return true
    }

    override suspend fun writeSafetySnapshot(): File = checkNotNull(snapshotFile) { "Set snapshotFile first" }

    override suspend fun latestSafetySnapshot(): File? = snapshotFile
}
