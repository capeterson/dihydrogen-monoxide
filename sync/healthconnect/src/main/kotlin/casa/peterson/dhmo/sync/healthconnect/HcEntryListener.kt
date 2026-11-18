package casa.peterson.dhmo.sync.healthconnect

import casa.peterson.dhmo.core.data.repository.EntryChangeListener
import casa.peterson.dhmo.core.data.repository.HealthConnectController
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.model.ChangeOrigin
import casa.peterson.dhmo.core.model.EntryChange
import javax.inject.Inject
import javax.inject.Provider

/**
 * A local change queues a debounced sync when writing is on. Changes that came from Health Connect itself never do,
 * or an import would trigger the push that follows it. The controller is a [Provider] to avoid a dependency cycle
 * with the engine, which announces its own changes to the same listeners.
 */
internal class HcEntryListener @Inject constructor(
    private val settings: SettingsRepository,
    private val controller: Provider<HealthConnectController>,
) : EntryChangeListener {
    override suspend fun onEntriesChanged(change: EntryChange) {
        if (change.origin == ChangeOrigin.HEALTH_CONNECT) return
        if (settings.current().healthConnect.writeHydration) controller.get().requestSync(immediate = false)
    }
}
