package casa.peterson.dhmo.core.reminders

/** The notification-id and `PendingIntent` request-code registry (TECHNICAL_PLAN §8.8). Other modules use their own ranges. */
object NotificationIds {
    const val NOTIF_REMINDER = 1001
    const val NOTIF_SUMMARY = 1002

    /** Owned by `:sync:drive`. */
    const val NOTIF_BACKUP_ERROR = 2001

    /** Owned by `:sync:healthconnect`. */
    const val NOTIF_HC_ERROR = 3001

    const val RC_ALARM = 1000
    const val RC_ACTION_LOG_1 = 1010
    const val RC_ACTION_LOG_2 = 1011
    const val RC_ACTION_SNOOZE = 1012
    const val RC_ACTION_CUSTOM = 1013
    const val RC_CONTENT = 1014
}
