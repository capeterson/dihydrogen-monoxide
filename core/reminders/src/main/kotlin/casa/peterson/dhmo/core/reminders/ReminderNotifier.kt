package casa.peterson.dhmo.core.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import casa.peterson.dhmo.core.domain.format.VolumeFormatter
import casa.peterson.dhmo.core.model.Container
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** What a reminder notification says (§8.8). */
sealed interface ReminderMessage {
    /** "You're 250 ml behind pace". */
    data class Nudge(val deficitMl: Int) : ReminderMessage

    /** "You're well behind today: 1,100 ml to go". */
    data class Escalation(val remainingMl: Int) : ReminderMessage
}

/** Builds and posts reminder notifications and owns the channels. `:app` calls [ensureChannels] at startup. */
@Singleton
class ReminderNotifier
@Inject
constructor(@ApplicationContext private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    /** Creates the `reminders`, `summaries` and `backup` channels (§8.8). Safe to call repeatedly. */
    fun ensureChannels() {
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        createChannel(
            system,
            "reminders",
            CHANNEL_REMINDERS,
            R.string.core_reminders_channel_reminders,
            R.string.core_reminders_channel_reminders_description,
            NotificationManager.IMPORTANCE_DEFAULT,
            R.raw.core_reminders_reminder_chime,
        )
        createChannel(
            system,
            "summaries",
            CHANNEL_SUMMARIES,
            R.string.core_reminders_channel_summaries,
            R.string.core_reminders_channel_summaries_description,
            NotificationManager.IMPORTANCE_LOW,
            R.raw.core_reminders_summary_chime,
        )
        createChannel(
            system,
            "backup",
            CHANNEL_BACKUP,
            R.string.core_reminders_channel_backup,
            R.string.core_reminders_channel_backup_description,
            NotificationManager.IMPORTANCE_MIN,
            R.raw.core_reminders_backup_chime,
        )
    }

    private fun createChannel(
        system: NotificationManager,
        oldId: String,
        id: String,
        name: Int,
        channelDescription: Int,
        importance: Int,
        soundResource: Int,
    ) {
        val old = system.getNotificationChannel(oldId)
        val channel = NotificationChannel(id, context.getString(name), old?.importance ?: importance).apply {
            description = context.getString(channelDescription)
            val soundName = context.resources.getResourceEntryName(soundResource)
            val customSound = "android.resource://${context.packageName}/raw/$soundName".toUri()
            val sound = if (old != null && old.sound != Settings.System.DEFAULT_NOTIFICATION_URI) old.sound else customSound
            setSound(sound, old?.audioAttributes ?: AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
            if (old != null) {
                enableVibration(old.shouldVibrate())
                vibrationPattern = old.vibrationPattern
                enableLights(old.shouldShowLights())
                lightColor = old.lightColor
                lockscreenVisibility = old.lockscreenVisibility
                setShowBadge(old.canShowBadge())
            }
        }
        system.createNotificationChannel(channel)
        // Channel sound is immutable after creation. Migrate once, then keep the new channel's user settings.
        if (old != null) system.deleteNotificationChannel(oldId)
    }

    /** True when the app may post notifications: the app-level switch and, on API 33+, `POST_NOTIFICATIONS`. */
    fun notificationsAllowed(): Boolean = manager.areNotificationsEnabled() &&
        (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            )

    /** Posts (or replaces) the single reminder notification, with log actions for the first two [containers]. */
    @SuppressLint("MissingPermission") // Checked by notificationsAllowed(); a revoked permission is caught below.
    fun showReminder(message: ReminderMessage, containers: List<Container>, units: VolumeUnit, snoozeMinutes: Int) {
        if (!notificationsAllowed()) return
        val locale = Locale.getDefault()
        val text = when (message) {
            is ReminderMessage.Nudge ->
                context.getString(R.string.core_reminders_nudge_text, VolumeFormatter.format(message.deficitMl, units, locale))

            is ReminderMessage.Escalation ->
                context.getString(R.string.core_reminders_escalation_text, VolumeFormatter.format(message.remainingMl, units, locale))
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.core_reminders_ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.core_reminders_accent))
            .setContentTitle(context.getString(R.string.core_reminders_nudge_title))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
        containers.sortedBy { it.sortOrder }.take(2).forEachIndexed { index, container ->
            val label = context.getString(
                R.string.core_reminders_action_log,
                container.name,
                VolumeFormatter.format(container.volumeMl, units, locale),
            )
            val requestCode = if (index == 0) NotificationIds.RC_ACTION_LOG_1 else NotificationIds.RC_ACTION_LOG_2
            builder.addAction(0, label, actionIntent(ReminderActionReceiver.logIntent(context, container.id), requestCode))
        }
        builder.addAction(
            0,
            context.getString(R.string.core_reminders_action_snooze, snoozeMinutes),
            actionIntent(ReminderActionReceiver.snoozeIntent(context), NotificationIds.RC_ACTION_SNOOZE),
        )
        builder.addAction(0, context.getString(R.string.core_reminders_action_custom), customIntent())
        post(NotificationIds.NOTIF_REMINDER, builder)
    }

    /** Posts the end-of-day summary on the low-importance `summaries` channel. */
    @SuppressLint("MissingPermission")
    fun showSummary(consumedMl: Int, goalMl: Int, units: VolumeUnit) {
        if (!notificationsAllowed()) return
        val locale = Locale.getDefault()
        val consumed = VolumeFormatter.format(consumedMl, units, locale)
        val goal = VolumeFormatter.format(goalMl, units, locale)
        val text = if (consumedMl >= goalMl) {
            context.getString(R.string.core_reminders_summary_met, consumed, goal)
        } else {
            context.getString(R.string.core_reminders_summary_short, consumed, goal)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_SUMMARIES)
            .setSmallIcon(R.drawable.core_reminders_ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.core_reminders_accent))
            .setContentTitle(context.getString(R.string.core_reminders_summary_title))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
        post(NotificationIds.NOTIF_SUMMARY, builder)
    }

    fun cancelReminder() = manager.cancel(NotificationIds.NOTIF_REMINDER)

    @SuppressLint("MissingPermission")
    private fun post(id: Int, builder: NotificationCompat.Builder) {
        try {
            manager.notify(id, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS was revoked after the check; the next reschedule cancels the alarm.
        }
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        NotificationIds.RC_CONTENT,
        Intent(Intent.ACTION_VIEW, TODAY_DEEP_LINK.toUri()).setPackage(context.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun customIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        NotificationIds.RC_ACTION_CUSTOM,
        Intent(ACTION_QUICK_LOG).setPackage(context.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun actionIntent(intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    companion object {
        const val CHANNEL_REMINDERS = "reminders_cozy"
        const val CHANNEL_SUMMARIES = "summaries_cozy"
        const val CHANNEL_BACKUP = "backup_cozy"

        /** Handled by `QuickLogActivity` in `:feature:today` (not exported, so only this package can start it). */
        const val ACTION_QUICK_LOG = "casa.peterson.dhmo.action.QUICK_LOG"
        const val TODAY_DEEP_LINK = "dhmo://today"
    }
}
