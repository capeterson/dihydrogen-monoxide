package casa.peterson.dhmo.core.data.repository

import casa.peterson.dhmo.core.model.EntryChange

/**
 * Called after every successful write in [IntakeRepository] (§6.4). Later modules contribute with
 * `@Binds @IntoSet`; a failing listener never fails the write.
 */
interface EntryChangeListener {
    suspend fun onEntriesChanged(change: EntryChange)
}
