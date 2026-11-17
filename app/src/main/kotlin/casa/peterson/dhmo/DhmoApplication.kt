package casa.peterson.dhmo

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import casa.peterson.dhmo.core.reminders.ReminderNotifier
import casa.peterson.dhmo.sync.wear.WearStateSync
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DhmoApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var reminderNotifier: ReminderNotifier

    @Inject lateinit var wearStateSync: WearStateSync

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        reminderNotifier.ensureChannels()
        wearStateSync.start()
    }
}
