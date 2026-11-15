package casa.peterson.dhmo.core.reminders

import javax.inject.Inject
import javax.inject.Singleton

/** Builds and posts reminder notifications and owns the channels (T13). `:app` calls [ensureChannels] at startup. */
@Singleton
class ReminderNotifier
@Inject
constructor() {
    /** Creates the `reminders`, `summaries` and `backup` channels (§8.8). */
    fun ensureChannels() {
        // Implemented by T13.
    }
}
