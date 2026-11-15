package casa.peterson.dhmo.core.data.repository

import casa.peterson.dhmo.core.model.Profile
import casa.peterson.dhmo.core.model.ProfileSnapshot
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Stub: the Room-backed implementation arrives with T4. */
class OfflineProfileRepository @Inject constructor() : ProfileRepository {
    override fun observeCurrent(): Flow<Profile?> = TODO("T4")

    override fun observeSnapshots(): Flow<List<ProfileSnapshot>> = TODO("T4")

    override suspend fun profileFor(day: LocalDate): ProfileSnapshot? = TODO("T4")

    override suspend fun save(profile: Profile, effectiveFrom: LocalDate) = TODO("T4")

    override suspend fun addHealthConnectWeight(weightKg: Double, effectiveFrom: LocalDate) = TODO("T4")
}
