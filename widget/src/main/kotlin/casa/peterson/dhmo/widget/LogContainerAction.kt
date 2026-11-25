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
import casa.peterson.dhmo.core.model.Source
import java.util.UUID

/** The container a button logs, as a UUID string. */
internal val ContainerIdKey = ActionParameters.Key<String>("container_id")

private const val TAG = "LogContainerAction"

/**
 * Logs the container named by [ContainerIdKey]: its volume, with source [Source.WIDGET]. A container that has been deleted
 * since the widget was drawn logs nothing. Returns whether a drink was logged.
 */
internal suspend fun logContainer(containers: ContainerRepository, logIntake: LogIntakeUseCase, containerId: String?): Boolean {
    val id = containerId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    val container = id?.let { wanted -> containers.getAll().firstOrNull { it.id == wanted } }
    container?.let { logIntake(it.volumeMl, Source.WIDGET) }
    return container != null
}

/** Logs the tapped container, then repaints every widget (also when the container is gone, so the stale button disappears). */
class LogContainerAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entry = entryPoint(context)
        suspendRunCatching { logContainer(entry.containers(), entry.logIntake(), parameters[ContainerIdKey]) }
            .onFailure { Log.w(TAG, "Could not log from the widget", it) }
        TodayWidget().updateAll(context)
    }
}

/**
 * What widgets placed before the per-container buttons call from their pending intents. It logs the default container (the
 * lowest sort order), so those widgets keep working until they next redraw with the new buttons.
 */
@Deprecated("Replaced by LogContainerAction; kept so pending intents of already placed widgets do not crash.")
class LogDefaultDrinkAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entry = entryPoint(context)
        suspendRunCatching {
            val default = entry.containers().getAll().minByOrNull { it.sortOrder }
            logContainer(entry.containers(), entry.logIntake(), default?.id?.toString())
        }.onFailure { Log.w(TAG, "Could not log the default drink from the widget", it) }
        TodayWidget().updateAll(context)
    }
}
