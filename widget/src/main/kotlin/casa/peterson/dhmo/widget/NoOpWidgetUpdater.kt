package casa.peterson.dhmo.widget

import casa.peterson.dhmo.core.data.repository.WidgetUpdater
import javax.inject.Inject

/** Replaced by the Glance implementation in Phase 6. */
internal class NoOpWidgetUpdater
@Inject
constructor() : WidgetUpdater {
    override suspend fun updateAll() = Unit
}
