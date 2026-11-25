package casa.peterson.dhmo.widget

import android.content.Context
import android.text.format.DateFormat
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import casa.peterson.dhmo.core.data.repository.ContainerRepository
import casa.peterson.dhmo.core.data.repository.DeviceStateRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.usecase.intake.LogIntakeUseCase
import casa.peterson.dhmo.core.data.usecase.today.ObserveTodayUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.first

/** What the widget needs from the app graph, read through an entry point because Glance builds its own objects. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {
    fun observeToday(): ObserveTodayUseCase

    fun containers(): ContainerRepository

    fun settings(): SettingsRepository

    fun deviceState(): DeviceStateRepository

    fun logIntake(): LogIntakeUseCase
}

internal fun entryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

/** Reads the repositories and hands the result to [buildWidgetModel]. */
internal suspend fun loadModel(context: Context): WidgetModel {
    val entry = entryPoint(context)
    return buildWidgetModel(
        snapshot = entry.observeToday()().first(),
        containers = entry.containers().getAll(),
        units = entry.settings().current().units,
        locale = Locale.getDefault(),
        onboarded = entry.deviceState().current().onboarding.completed,
        resources = context.resources,
        zone = ZoneId.systemDefault(),
        is24Hour = DateFormat.is24HourFormat(context),
    )
}

/**
 * Today's progress with one-tap add buttons. One widget, six layouts: the launcher gives Glance the real size and the
 * layout is the breakpoint (see [WidgetBreakpoints]) that fits it best. The four picker entries differ only in size.
 */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(WidgetBreakpoints.map { it.size }.toSet())

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = loadModel(context)
        provideContent { WidgetRoot(model) }
    }

    /** The picker's generated preview: a typical afternoon, so the preview shows what the widget does. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val model = sampleModel(context.resources, WidgetSample.BEHIND)
        provideContent { WidgetRoot(model) }
    }
}
