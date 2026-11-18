package casa.peterson.dhmo.sync.healthconnect

import android.content.Context
import android.content.pm.PackageManager
import casa.peterson.dhmo.core.data.repository.HealthConnectController
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.database.dao.HcSyncDao
import casa.peterson.dhmo.core.database.dao.IntakeDao
import casa.peterson.dhmo.sync.healthconnect.engine.HealthConnectSyncEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject

/** Switching a source app or the whole import off hides what it brought; switching on again brings it back (§10.6). */
internal class HealthConnectSources @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val intakeDao: IntakeDao,
    private val hcDao: HcSyncDao,
    private val engine: HealthConnectSyncEngine,
    private val controller: HealthConnectController,
    private val clock: Clock,
) {
    suspend fun setIncluded(packageName: String, included: Boolean) {
        settings.update { s -> s.copy(healthConnect = s.healthConnect.copy(importSources = s.healthConnect.importSources + (packageName to included))) }
        if (included) {
            hcDao.deleteState(HealthConnectSyncEngine.TOKEN) // the next sync reconciles and brings its records back
            controller.requestSync(immediate = true)
        } else {
            hideImportsFrom(packageName)
        }
    }

    /** Called when `importHydration` goes off: every live, non-adopted import is hidden, the source list is kept. */
    suspend fun hideAllImports() {
        settings.current().healthConnect.importSources.keys.forEach { hideImportsFrom(it) }
        hcDao.deleteState(HealthConnectSyncEngine.TOKEN)
    }

    private suspend fun hideImportsFrom(packageName: String) {
        val now = clock.instant()
        val rows = intakeDao.liveImportsFrom(packageName)
        rows.forEach { intakeDao.softDelete(it.id, now) }
        if (rows.isNotEmpty()) engine.announce(rows.map { it.hydrationDay }.toSet())
    }

    /** The label the user knows the app by, or the package name when it is not installed. */
    fun label(packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }
}
