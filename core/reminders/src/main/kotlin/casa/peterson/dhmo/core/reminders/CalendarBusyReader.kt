package casa.peterson.dhmo.core.reminders

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import casa.peterson.dhmo.core.domain.pace.BusyInterval
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject

/** Busy calendar events overlapping a time range (§8.6). Empty without `READ_CALENDAR`. */
fun interface CalendarBusyReader {
    fun busy(from: Instant, to: Instant): List<BusyInterval>
}

internal class ContentResolverCalendarBusyReader @Inject constructor(@ApplicationContext private val context: Context) :
    CalendarBusyReader {
    override fun busy(from: Instant, to: Instant): List<BusyInterval> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(from.toEpochMilli().toString())
            .appendPath(to.toEpochMilli().toString())
            .build()
        val projection = arrayOf(CalendarContract.Instances.BEGIN, CalendarContract.Instances.END)
        val selection = "${CalendarContract.Instances.AVAILABILITY} = ? AND ${CalendarContract.Instances.ALL_DAY} = 0"
        val args = arrayOf(CalendarContract.Events.AVAILABILITY_BUSY.toString())
        return try {
            context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val begin = cursor.getLong(0)
                        val end = cursor.getLong(1)
                        if (end > begin) add(BusyInterval(Instant.ofEpochMilli(begin), Instant.ofEpochMilli(end)))
                    }
                }.sortedBy { it.start }
            }.orEmpty()
        } catch (_: SecurityException) {
            // The permission was revoked between the check and the query.
            emptyList()
        }
    }
}
