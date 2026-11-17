package casa.peterson.dhmo

import androidx.test.core.app.ApplicationProvider
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.core.model.settings.DeviceState
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/**
 * Resets the database and both settings stores before the activity starts, so every test sees a fresh install
 * regardless of test order. Put it in an outer [org.junit.rules.RuleChain] around the compose rule.
 */
class FreshInstallRule : ExternalResource() {
    val hooks: TestHooksEntryPoint by lazy {
        EntryPointAccessors.fromApplication(ApplicationProvider.getApplicationContext(), TestHooksEntryPoint::class.java)
    }

    override fun before() = runBlocking {
        hooks.database().clearAllTables()
        hooks.settings().update { AppSettings() }
        hooks.deviceState().update { DeviceState() }
    }
}
