package casa.peterson.dhmo.widget

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import casa.peterson.dhmo.core.data.repository.ContainerRepository
import casa.peterson.dhmo.core.data.repository.DeviceStateRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.usecase.intake.DeleteIntakeUseCase
import casa.peterson.dhmo.core.data.usecase.intake.LogIntakeUseCase
import casa.peterson.dhmo.core.data.usecase.today.ObserveTodayUseCase
import casa.peterson.dhmo.core.data.usecase.today.TodaySnapshot
import casa.peterson.dhmo.core.model.Container
import casa.peterson.dhmo.core.model.settings.AppSettings
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** What the widget needs from the app graph, read through an entry point because Glance builds its own objects. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {
    fun observeToday(): ObserveTodayUseCase

    fun containers(): ContainerRepository

    fun settings(): SettingsRepository

    fun deviceState(): DeviceStateRepository

    fun logIntake(): LogIntakeUseCase

    fun deleteIntake(): DeleteIntakeUseCase
}

internal fun entryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

private const val SECONDS_PER_MINUTE = 60L

/** What one drawing of the widget needs: the model and the drawings made from it. */
internal class WidgetFrame(val model: WidgetModel, val art: WidgetArt)

/** The app state the widget draws, gathered from every flow it depends on. */
private class WidgetInputs(
    val snapshot: TodaySnapshot,
    val containers: List<Container>,
    val settings: AppSettings,
    val onboarded: Boolean,
    val undo: PendingUndo?,
)

/**
 * The widget's content as it changes: today's total and goal, containers, settings, onboarding, the Undo offer and the
 * system's light or dark mode. Collected inside the composition, so a widget whose Glance session is still running
 * redraws on every change; data read once before `provideContent` would stay frozen until the session closed, and the
 * total would lag behind the taps.
 *
 * Each new model also schedules the repaint at the next day boundary, the one change no flow announces while the widget's
 * session is closed.
 */
internal fun widgetFrames(context: Context): Flow<WidgetFrame> {
    val entry = entryPoint(context)
    val inputs = combine(
        entry.observeToday()(),
        entry.containers().observeAll(),
        entry.settings().settings,
        entry.deviceState().state.map { it.onboarding.completed },
        WidgetUndoStore(context).offers(),
    ) { snapshot, containers, settings, onboarded, undo -> WidgetInputs(snapshot, containers, settings, onboarded, undo) }
    return combine(inputs, WidgetConfiguration.changes) { input, _ ->
        val zone = ZoneId.systemDefault()
        val dayStart = LocalTime.ofSecondOfDay(input.settings.dayStartMinutes * SECONDS_PER_MINUTE)
        WidgetRefreshScheduler.atNextDay(context, input.snapshot.day.plusDays(1).atTime(dayStart).atZone(zone).toInstant(), Instant.now())
        buildWidgetModel(
            snapshot = input.snapshot,
            containers = input.containers,
            units = input.settings.units,
            locale = Locale.getDefault(),
            onboarded = input.onboarded,
            night = resolveNight(input.settings.theme.mode, context.resources.configuration),
            resources = context.resources,
            zone = zone,
            is24Hour = DateFormat.is24HourFormat(context),
            undo = input.undo,
        )
    }
        .distinctUntilChanged()
        .map { model -> WidgetFrame(model, WidgetArt.render(model, context.resources.displayMetrics.density)) }
        .flowOn(Dispatchers.Default)
}

/**
 * Today's jar with one-tap coasters. One widget, six layouts: the launcher gives Glance the real size and the layout is the
 * breakpoint (see [WidgetBreakpoints]) that fits it best. The four picker entries differ only in their starting size.
 */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(WidgetBreakpoints.map { it.size }.toSet())

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val frames = widgetFrames(context)
        val first = frames.first()
        provideContent {
            val frame by frames.collectAsState(first)
            WidgetRoot(frame.model, frame.art)
        }
    }

    /**
     * The picker's generated preview on Android 15 and later: an afternoon on track, the same state as the static
     * `widget_preview_*` images that older pickers show.
     */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val model = sampleModel(context.resources, WidgetSample.ON_TRACK)
        val art = WidgetArt.render(model, context.resources.displayMetrics.density)
        provideContent { WidgetRoot(model, art) }
    }
}
