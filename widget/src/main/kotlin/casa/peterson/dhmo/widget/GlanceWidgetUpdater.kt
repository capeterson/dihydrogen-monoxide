package casa.peterson.dhmo.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import casa.peterson.dhmo.core.data.repository.EntryChangeListener
import casa.peterson.dhmo.core.data.repository.WidgetUpdater
import casa.peterson.dhmo.core.model.EntryChange
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal class GlanceWidgetUpdater @Inject constructor(@param:ApplicationContext private val context: Context) : WidgetUpdater {
    override suspend fun updateAll() = TodayWidget().updateAll(context)
}

/** Every logged, edited or deleted drink refreshes the widget. A listener failure never fails the write. */
internal class WidgetEntryListener @Inject constructor(private val updater: WidgetUpdater) : EntryChangeListener {
    override suspend fun onEntriesChanged(change: EntryChange) = updater.updateAll()
}
