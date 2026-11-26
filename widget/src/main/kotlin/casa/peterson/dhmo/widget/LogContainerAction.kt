package casa.peterson.dhmo.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import casa.peterson.dhmo.core.common.suspendRunCatching
import casa.peterson.dhmo.core.data.repository.ContainerRepository
import casa.peterson.dhmo.core.data.usecase.intake.LogIntakeUseCase
import casa.peterson.dhmo.core.model.IntakeEntry
import casa.peterson.dhmo.core.model.Source
import java.time.Instant
import java.util.UUID

/** The container a coaster logs, as a UUID string. */
internal val ContainerIdKey = ActionParameters.Key<String>("container_id")

private const val TAG = "LogContainerAction"

/**
 * Logs the container named by [containerId]: its volume, with source [Source.WIDGET]. A container deleted since the widget
 * was drawn logs nothing. Returns the new entry, or null when nothing was logged.
 */
internal suspend fun logContainer(containers: ContainerRepository, logIntake: LogIntakeUseCase, containerId: String?): IntakeEntry? {
    val id = containerId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    val container = id?.let { wanted -> containers.getAll().firstOrNull { it.id == wanted } }
    return container?.let { logIntake(it.volumeMl, Source.WIDGET) }
}

/**
 * Logs the tapped coaster's container, offers Undo for a few seconds, and repaints every widget (also when the container
 * is gone, so the stale coaster disappears).
 */
class LogContainerAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entry = entryPoint(context)
        suspendRunCatching { logContainer(entry.containers(), entry.logIntake(), parameters[ContainerIdKey]) }
            .onSuccess { logged -> logged?.let { offerUndo(context, it) } }
            .onFailure { Log.w(TAG, "Could not log from the widget", it) }
        TodayWidget().updateAll(context)
    }
}

/** Remembers [logged] for Undo and repaints the widgets once the offer has run out. */
internal fun offerUndo(context: Context, logged: IntakeEntry) {
    WidgetUndoStore(context).record(logged.id, logged.volumeMl, Instant.now())
    WidgetRefreshScheduler.afterUndoWindow(context)
}

/**
 * What widgets placed before per-container coasters call from their pending intents. It logs the default container (the
 * lowest sort order), so those widgets keep working until they next redraw.
 */
@Deprecated("Replaced by LogContainerAction; kept so pending intents of already placed widgets do not crash.")
class LogDefaultDrinkAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entry = entryPoint(context)
        suspendRunCatching {
            val default = entry.containers().getAll().minByOrNull { it.sortOrder }
            logContainer(entry.containers(), entry.logIntake(), default?.id?.toString())
        }.onSuccess { logged -> logged?.let { offerUndo(context, it) } }
            .onFailure { Log.w(TAG, "Could not log the default drink from the widget", it) }
        TodayWidget().updateAll(context)
    }
}
