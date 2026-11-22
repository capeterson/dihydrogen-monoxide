package casa.peterson.dhmo.sync.drive.work

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import casa.peterson.dhmo.sync.drive.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The "Reconnect Google Drive" notification (§11.5): a worker cannot show the consent screen, so it asks the user to. */
internal class ReconnectNotifier @Inject constructor(@param:ApplicationContext private val context: Context) {
    @SuppressLint("MissingPermission") // checked through areNotificationsEnabled
    fun notifyReconnectNeeded() {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val open = Intent(Intent.ACTION_VIEW, "dhmo://settings/backup".toUri()).setPackage(context.packageName)
        val tap = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_BACKUP)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(context.getString(R.string.sync_drive_reconnect_title))
            .setContentText(context.getString(R.string.sync_drive_reconnect_text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel() = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    companion object {
        /** Created by the app at start-up together with the reminder channels. */
        const val CHANNEL_BACKUP = "backup_cozy"
        private const val NOTIFICATION_ID = 4_201
    }
}
