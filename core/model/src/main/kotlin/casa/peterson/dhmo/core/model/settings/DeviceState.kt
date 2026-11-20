package casa.peterson.dhmo.core.model.settings

import kotlinx.serialization.Serializable

@Serializable
data class DeviceState(
    val schemaVersion: Int = 1,
    val onboarding: OnboardingState = OnboardingState(),
    val cachedLocation: CachedLocation? = null, // DEVICE_LOCATION mode
    val reminderRuntime: ReminderRuntime = ReminderRuntime(),
    val backupRuntime: BackupRuntime = BackupRuntime(),
    val weatherRuntime: WeatherRuntime = WeatherRuntime(),
    /** Device-local: a goal celebration is a one-time moment, never replayed after undo or process recreation. */
    val lastCelebratedGoalDay: Long? = null,
)

@Serializable
data class OnboardingState(
    val completed: Boolean = false,
    val disclaimerAcceptedAtMs: Long? = null,
    val manualModeRecommended: Boolean = false, // user ticked "I have a condition / fluid restriction"
    val notificationRationaleShown: Boolean = false,
    val lastStep: String? = null, // route name, to resume after process death
    val draft: OnboardingDraft? = null,
)

/** Device-local setup inputs, retained until onboarding is completed. */
@Serializable
data class OnboardingDraft(
    val choice: String = "CALCULATED",
    val birthDate: String = "",
    val weight: String = "",
    val height: String = "",
    val heightFeet: String = "",
    val heightInches: String = "",
    val gender: String = "UNSPECIFIED",
    val activity: String = "SEDENTARY",
    val climate: String = "TEMPERATE",
    val pregnant: Boolean = false,
    val lactating: Boolean = false,
    val altitude: Boolean = false,
    val manualGoal: String? = null,
    val remindersEnabled: Boolean = false,
    val notificationsGranted: Boolean = false,
    val wake: Int = 420,
    val sleep: Int = 1380,
)

@Serializable
data class CachedLocation(val latE1: Int, val lonE1: Int, val obtainedAtMs: Long) // already rounded to 0.1°

@Serializable
data class ReminderRuntime(
    val scheduledAtMs: Long? = null, // what the pending alarm was set for
    val scheduledKind: String? = null, // NUDGE, SUMMARY, RECHECK
    val lastReminderAtMs: Long? = null,
    val snoozedUntilMs: Long? = null,
    val escalationSentDay: Long? = null, // epoch day
    val summarySentDay: Long? = null, // epoch day
    val missedAlarmTimesMs: List<Long> = emptyList(), // last 10 late deliveries (§8.9)
    val batteryHintDismissed: Boolean = false,
)

@Serializable
data class BackupRuntime(
    val accountEmail: String? = null,
    val lastSuccessAtMs: Long? = null,
    val lastErrorAtMs: Long? = null,
    val lastError: String? = null, // BackupError enum name
    val needsReconnect: Boolean = false,
    val wrappedKeyB64: String? = null, // backup key wrapped by an Android Keystore key (§11.4)
    val kdfSaltB64: String? = null,
)

@Serializable
data class WeatherRuntime(val lastFetchAtMs: Long? = null, val lastFetchFailed: Boolean = false)
