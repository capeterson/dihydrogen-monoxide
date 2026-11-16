package casa.peterson.dhmo.core.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import casa.peterson.dhmo.core.common.ZoneProvider
import casa.peterson.dhmo.core.data.repository.ContainerRepository
import casa.peterson.dhmo.core.data.repository.DeviceStateRepository
import casa.peterson.dhmo.core.data.repository.ReminderScheduler
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.usecase.goal.SnapshotDailyGoalUseCase
import casa.peterson.dhmo.core.domain.pace.AlarmKind
import casa.peterson.dhmo.core.domain.pace.ReminderKind
import casa.peterson.dhmo.core.domain.pace.nextReminder
import casa.peterson.dhmo.core.domain.time.hydrationDay
import casa.peterson.dhmo.core.model.RescheduleReason
import casa.peterson.dhmo.core.model.settings.ReminderRuntime
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The only class that touches [AlarmManager] (§8.6–8.7). Exactly one inexact, Doze-safe alarm exists at a time: every
 * call reuses the same [PendingIntent], so scheduling replaces the previous alarm. No exact-alarm permission is used.
 */
@Singleton
class AlarmReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val deviceState: DeviceStateRepository,
    private val containers: ContainerRepository,
    private val buildState: BuildReminderStateUseCase,
    // A Provider breaks the cycle: the snapshot use case itself calls reschedule(GOAL_CHANGED).
    private val snapshot: Provider<SnapshotDailyGoalUseCase>,
    private val notifier: ReminderNotifier,
    private val rollover: RolloverWorkScheduler,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider,
) : ReminderScheduler {
    private val mutex = Mutex()
    private val alarmManager: AlarmManager = checkNotNull(context.getSystemService(AlarmManager::class.java))

    override suspend fun reschedule(reason: RescheduleReason) = mutex.withLock {
        rollover.ensureScheduled()
        val now = clock.instant()
        if (!remindersActive()) {
            cancelLocked()
            return@withLock
        }
        snapshot.get()(today(now), notifyReminders = false)
        val state = buildState(now) ?: run {
            cancelLocked()
            return@withLock
        }
        val decision = nextReminder(state, now)
        // Only the alarm posts reminders, so logging a drink never produces a notification. A reminder that is due
        // right now is delivered by an alarm one minute from now instead of being dropped.
        val due = decision.fireNow
        val (at, kind) = if (due != null) {
            now.plus(DUE_NOW_DELAY) to alarmKindFor(due)
        } else {
            decision.nextAlarmAt to decision.nextAlarmKind
        }
        if (at == null || kind == null) cancelLocked() else setAlarmLocked(at, kind)
    }

    override suspend fun cancel() = mutex.withLock { cancelLocked() }

    override suspend fun snooze() {
        val minutes = settings.current().reminders.snoozeMinutes
        val until = clock.instant().plus(Duration.ofMinutes(minutes.toLong()))
        deviceState.update { it.copy(reminderRuntime = it.reminderRuntime.copy(snoozedUntilMs = until.toEpochMilli())) }
        notifier.cancelReminder()
        reschedule(RescheduleReason.SNOOZE)
    }

    /**
     * Handles a delivered alarm (§8.7): record a missed alarm (§8.9), post whatever is due, and schedule the next one.
     * [scheduledAtMs] is the extra the alarm carried, or null when it was missing.
     */
    suspend fun onAlarm(scheduledAtMs: Long?) = mutex.withLock {
        rollover.ensureScheduled()
        val now = clock.instant()
        recordMissedAlarm(now, scheduledAtMs ?: deviceState.current().reminderRuntime.scheduledAtMs)
        if (!remindersActive()) {
            cancelLocked()
            return@withLock
        }
        val day = today(now)
        snapshot.get()(day, notifyReminders = false)
        val state = buildState(now) ?: run {
            cancelLocked()
            return@withLock
        }
        val decision = nextReminder(state, now)
        val current = settings.current()
        when (val fire = decision.fireNow) {
            ReminderKind.NUDGE, ReminderKind.ESCALATION -> {
                val message = if (fire == ReminderKind.ESCALATION) {
                    ReminderMessage.Escalation((state.goalMl - state.consumedMl).coerceAtLeast(0))
                } else {
                    ReminderMessage.Nudge(decision.deficitMl.coerceAtLeast(0))
                }
                notifier.showReminder(message, containers.getAll(), current.units, current.reminders.snoozeMinutes)
                updateRuntime {
                    copy(
                        lastReminderAtMs = now.toEpochMilli(),
                        escalationSentDay = if (fire == ReminderKind.ESCALATION) day.toEpochDay() else escalationSentDay,
                    )
                }
            }

            ReminderKind.SUMMARY -> {
                notifier.showSummary(state.consumedMl, state.goalMl, current.units)
                updateRuntime { copy(summarySentDay = day.toEpochDay()) }
            }

            null -> Unit
        }
        updateRuntime { copy(snoozedUntilMs = snoozedUntilMs?.takeIf { it > now.toEpochMilli() }) }
        val at = decision.nextAlarmAt
        val kind = decision.nextAlarmKind
        if (at == null || kind == null) cancelLocked() else setAlarmLocked(at, kind)
    }

    private suspend fun remindersActive(): Boolean = settings.current().reminders.enabled && notifier.notificationsAllowed()

    private suspend fun today(now: Instant): LocalDate {
        val zone = zoneProvider.current()
        return hydrationDay(now, zone.rules.getOffset(now), minutesToTime(settings.current().dayStartMinutes))
    }

    private suspend fun recordMissedAlarm(now: Instant, scheduledAtMs: Long?) {
        if (scheduledAtMs == null || now.toEpochMilli() - scheduledAtMs <= MISSED_THRESHOLD.toMillis()) return
        updateRuntime { copy(missedAlarmTimesMs = (missedAlarmTimesMs + now.toEpochMilli()).takeLast(MISSED_KEEP)) }
    }

    private suspend fun setAlarmLocked(at: Instant, kind: AlarmKind) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), alarmIntent(at, kind))
        updateRuntime { copy(scheduledAtMs = at.toEpochMilli(), scheduledKind = kind.name) }
    }

    private suspend fun cancelLocked() {
        alarmManager.cancel(alarmIntent(null, null))
        updateRuntime { copy(scheduledAtMs = null, scheduledKind = null) }
    }

    private fun alarmIntent(at: Instant?, kind: AlarmKind?): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).setAction(ReminderAlarmReceiver.ACTION_ALARM)
        if (at != null) intent.putExtra(ReminderAlarmReceiver.EXTRA_SCHEDULED_AT_MS, at.toEpochMilli())
        if (kind != null) intent.putExtra(ReminderAlarmReceiver.EXTRA_KIND, kind.name)
        return PendingIntent.getBroadcast(
            context,
            NotificationIds.RC_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private suspend fun updateRuntime(transform: ReminderRuntime.() -> ReminderRuntime) {
        deviceState.update { it.copy(reminderRuntime = it.reminderRuntime.transform()) }
    }

    internal companion object {
        val DUE_NOW_DELAY: Duration = Duration.ofMinutes(1)

        /** Late delivery up to about 15 minutes is normal in Doze; beyond 30 minutes it counts as missed (§8.9). */
        val MISSED_THRESHOLD: Duration = Duration.ofMinutes(30)
        const val MISSED_KEEP = 10
    }
}

private fun alarmKindFor(kind: ReminderKind): AlarmKind = if (kind == ReminderKind.SUMMARY) AlarmKind.SUMMARY else AlarmKind.NUDGE
