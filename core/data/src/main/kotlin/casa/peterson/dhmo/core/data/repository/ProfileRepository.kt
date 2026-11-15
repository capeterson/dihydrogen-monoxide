package casa.peterson.dhmo.core.data.repository

import casa.peterson.dhmo.core.model.Profile
import casa.peterson.dhmo.core.model.ProfileSnapshot
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun observeCurrent(): Flow<Profile?>

    fun observeSnapshots(): Flow<List<ProfileSnapshot>>

    /** Applies the resolution and pinWeight rules of §4.3. */
    suspend fun profileFor(day: LocalDate): ProfileSnapshot?

    /** source = MANUAL. */
    suspend fun save(profile: Profile, effectiveFrom: LocalDate)

    suspend fun addHealthConnectWeight(weightKg: Double, effectiveFrom: LocalDate)
}
