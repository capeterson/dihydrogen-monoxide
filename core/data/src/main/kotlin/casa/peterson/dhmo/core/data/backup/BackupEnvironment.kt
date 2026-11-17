package casa.peterson.dhmo.core.data.backup

import android.content.Context
import android.os.Build
import android.provider.Settings
import casa.peterson.dhmo.core.common.ZoneProvider
import casa.peterson.dhmo.core.data.repository.ReminderScheduler
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.repository.WidgetUpdater
import casa.peterson.dhmo.core.data.usecase.goal.SnapshotDailyGoalUseCase
import casa.peterson.dhmo.core.domain.time.hydrationDay
import casa.peterson.dhmo.core.model.RescheduleReason
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Clock
import java.time.LocalTime
import javax.inject.Inject

/** Device facts written into a backup, and where safety snapshots live. */
interface BackupEnvironment {
    val appVersionName: String
    val appVersionCode: Int
    val deviceName: String

    /** `files/safety/`, excluded from Android Auto Backup (§11.11). */
    val safetyDir: File
}

class AndroidBackupEnvironment @Inject constructor(@ApplicationContext private val context: Context) : BackupEnvironment {
    private val packageInfo by lazy { context.packageManager.getPackageInfo(context.packageName, 0) }

    override val appVersionName: String get() = packageInfo.versionName.orEmpty()

    @Suppress("DEPRECATION") // longVersionCode needs API 28; the version code fits in an Int
    override val appVersionCode: Int get() = packageInfo.versionCode

    override val deviceName: String
        get() = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL

    override val safetyDir: File get() = File(context.filesDir, "safety")
}

/** Step 4 of §11.9: re-derive state that depends on the restored rows and settings. */
fun interface AfterRestore {
    suspend fun onRestored()
}

class DefaultAfterRestore
@Inject
constructor(
    private val snapshotGoal: SnapshotDailyGoalUseCase,
    private val reminders: ReminderScheduler,
    private val widgets: WidgetUpdater,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
) : AfterRestore {
    override suspend fun onRestored() {
        val now = clock.instant()
        val dayStart = LocalTime.ofSecondOfDay(settings.current().dayStartMinutes * SECONDS_PER_MINUTE)
        snapshotGoal(hydrationDay(now, zoneProvider.current().rules.getOffset(now), dayStart), notifyReminders = false)
        reminders.reschedule(RescheduleReason.SETTINGS_CHANGED)
        widgets.updateAll()
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60L
    }
}
