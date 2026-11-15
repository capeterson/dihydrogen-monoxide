package casa.peterson.dhmo.core.data.repository

import casa.peterson.dhmo.core.data.mapper.toEntity
import casa.peterson.dhmo.core.data.mapper.toModel
import casa.peterson.dhmo.core.database.TransactionRunner
import casa.peterson.dhmo.core.database.dao.ProfileDao
import casa.peterson.dhmo.core.model.Profile
import casa.peterson.dhmo.core.model.ProfileSnapshot
import casa.peterson.dhmo.core.model.ProfileSource
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Picks the snapshot in force on a date (§4.3). */
internal object ProfileResolver {
    /**
     * The row with the greatest `(effectiveFrom, createdAt)` such that `effectiveFrom <= day`. With [pinWeight],
     * Health Connect rows are skipped. When no row is early enough, the earliest row is used.
     */
    fun resolve(snapshots: List<ProfileSnapshot>, day: LocalDate, pinWeight: Boolean): ProfileSnapshot? {
        val candidates = if (pinWeight) snapshots.filter { it.source == ProfileSource.MANUAL } else snapshots
        val order = compareBy<ProfileSnapshot>({ it.effectiveFrom }, { it.createdAt })
        return candidates.filter { it.effectiveFrom <= day }.maxWithOrNull(order) ?: candidates.minWithOrNull(order)
    }
}

/** Room-backed [ProfileRepository]; resolution is done in Kotlin through [ProfileResolver]. */
class OfflineProfileRepository
@Inject
constructor(
    private val dao: ProfileDao,
    private val transactions: TransactionRunner,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ProfileRepository {
    override fun observeCurrent(): Flow<Profile?> = observeSnapshots()
        .combine(settings.settings) { snapshots, current ->
            ProfileResolver.resolve(snapshots, LocalDate.MAX, current.goal.pinned.pinWeight)?.profile
        }

    override fun observeSnapshots(): Flow<List<ProfileSnapshot>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun profileFor(day: LocalDate): ProfileSnapshot? {
        val snapshots = dao.getAll().map { it.toModel() }
        return ProfileResolver.resolve(snapshots, day, settings.current().goal.pinned.pinWeight)
    }

    override suspend fun save(profile: Profile, effectiveFrom: LocalDate) {
        val snapshot = ProfileSnapshot(
            id = UUID.randomUUID(),
            effectiveFrom = effectiveFrom,
            profile = profile,
            source = ProfileSource.MANUAL,
            createdAt = clock.instant(),
        )
        transactions.run {
            dao.deleteManualOn(effectiveFrom)
            dao.insert(snapshot.toEntity())
        }
    }

    override suspend fun addHealthConnectWeight(weightKg: Double, effectiveFrom: LocalDate) {
        val now = clock.instant()
        transactions.run {
            val latest = dao.getAll().lastOrNull()?.toModel() ?: return@run
            val copy = latest.copy(
                id = UUID.randomUUID(),
                effectiveFrom = effectiveFrom,
                profile = latest.profile.copy(weightKg = weightKg),
                source = ProfileSource.HEALTH_CONNECT,
                createdAt = now,
            )
            dao.insert(copy.toEntity())
        }
    }
}
