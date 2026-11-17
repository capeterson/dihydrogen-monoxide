package casa.peterson.dhmo

import casa.peterson.dhmo.core.data.repository.BackupRepository
import casa.peterson.dhmo.core.data.repository.DeviceStateRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.database.DhmoDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Debug-only access to app singletons for the end-to-end tests in `app/src/androidTest`, so each test can start
 * from a fresh install state without the Hilt test application (which would skip `DhmoApplication` start-up).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TestHooksEntryPoint {
    fun database(): DhmoDatabase

    fun settings(): SettingsRepository

    fun deviceState(): DeviceStateRepository

    fun backup(): BackupRepository
}
