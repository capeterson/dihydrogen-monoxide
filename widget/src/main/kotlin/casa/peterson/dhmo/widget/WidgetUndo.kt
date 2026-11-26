package casa.peterson.dhmo.widget

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import casa.peterson.dhmo.core.common.suspendRunCatching
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/** The entry an Undo removes, as a UUID string. */
internal val EntryIdKey = ActionParameters.Key<String>("entry_id")

private const val TAG = "WidgetUndo"

/**
 * The last drink logged from a widget, offered for [UNDO_WINDOW] so a mistaken tap on the home screen can be taken back,
 * as the Today screen does with its snackbar. Shared by every widget, since all of them show the same day.
 */
internal class WidgetUndoStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun record(entryId: UUID, volumeMl: Int, now: Instant) {
        prefs.edit {
            putString(KEY_ID, entryId.toString())
            putInt(KEY_VOLUME, volumeMl)
            putLong(KEY_EXPIRES, now.plus(UNDO_WINDOW).toEpochMilli())
        }
        Changes.value++
    }

    /** The drink to offer Undo for at [now], or null once its window has passed. */
    fun pending(now: Instant): PendingUndo? {
        val id = recordedId()
        val offered = now.toEpochMilli() < prefs.getLong(KEY_EXPIRES, 0L)
        return if (id != null && offered) PendingUndo(id, prefs.getInt(KEY_VOLUME, 0)) else null
    }

    /** Whether [entryId] is the drink last logged from a widget, which is the only one an Undo may remove. */
    fun isLatest(entryId: UUID): Boolean = recordedId() == entryId

    fun clear() {
        prefs.edit { clear() }
        Changes.value++
    }

    /**
     * The offer as it changes: the drink when one is logged, then null once [UNDO_WINDOW] has passed or it is undone. A
     * widget whose session is still running redraws from this, without waiting for a fresh update.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun offers(clock: () -> Instant = Instant::now): Flow<PendingUndo?> = Changes.flatMapLatest {
        flow {
            val now = clock()
            val offer = pending(now)
            emit(offer)
            if (offer != null) {
                delay(prefs.getLong(KEY_EXPIRES, 0L) - now.toEpochMilli())
                emit(null)
            }
        }
    }.distinctUntilChanged()

    private fun recordedId(): UUID? = prefs.getString(KEY_ID, null)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    companion object {
        val UNDO_WINDOW: Duration = Duration.ofSeconds(10)

        /** Bumped on every write, so [offers] re-reads the store. One store per process, so one counter. */
        private val Changes = MutableStateFlow(0L)
        private const val FILE = "widget_undo"
        private const val KEY_ID = "entry_id"
        private const val KEY_VOLUME = "volume_ml"
        private const val KEY_EXPIRES = "expires_at"
    }
}

/**
 * Removes the drink the widget just logged. Only that drink: an Undo whose entry is no longer the latest widget log (the
 * user has logged again since) does nothing, so a stale button can never delete the wrong entry. The removal is an
 * ordinary delete, so Health Connect, reminders and the widgets all follow it.
 */
class UndoLastDrinkAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val store = WidgetUndoStore(context)
        val id = parameters[EntryIdKey]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (id != null && store.isLatest(id)) {
            store.clear()
            suspendRunCatching { entryPoint(context).deleteIntake()(id) }.onFailure { Log.w(TAG, "Could not undo the widget's drink", it) }
        }
        TodayWidget().updateAll(context)
    }
}
