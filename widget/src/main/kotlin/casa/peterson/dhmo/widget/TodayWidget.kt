package casa.peterson.dhmo.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import casa.peterson.dhmo.core.designsystem.theme.DarkColors
import casa.peterson.dhmo.core.designsystem.theme.LightColors
import casa.peterson.dhmo.core.domain.format.VolumeFormatter
import casa.peterson.dhmo.core.model.Source
import casa.peterson.dhmo.core.model.settings.ThemeMode
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.Locale
import kotlinx.coroutines.flow.first

/** What the widget needs from the app graph, read through an entry point because Glance builds its own objects. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {
    fun observeToday(): casa.peterson.dhmo.core.data.usecase.today.ObserveTodayUseCase

    fun containers(): casa.peterson.dhmo.core.data.repository.ContainerRepository

    fun settings(): casa.peterson.dhmo.core.data.repository.SettingsRepository

    fun logIntake(): casa.peterson.dhmo.core.data.usecase.intake.LogIntakeUseCase
}

internal data class WidgetModel(
    val consumedText: String,
    val goalText: String?,
    val fraction: Float,
    val defaultLabel: String?,
    val hasDefault: Boolean,
    val themeMode: ThemeMode,
)

internal fun entryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

internal suspend fun loadModel(context: Context): WidgetModel {
    val ep = entryPoint(context)
    val today = ep.observeToday()().first()
    val settings = ep.settings().current()
    val unit = settings.units
    val locale = Locale.getDefault()
    val default = ep.containers().getAll().minByOrNull { it.sortOrder }
    val goal = today.goal?.targetMl
    return WidgetModel(
        consumedText = VolumeFormatter.format(today.totalMl, unit, locale),
        goalText = goal?.let { VolumeFormatter.format(it, unit, locale) },
        fraction = if (goal == null || goal <= 0) 0f else (today.totalMl.toFloat() / goal).coerceIn(0f, 1f),
        defaultLabel = default?.let { "+" + VolumeFormatter.format(it.volumeMl, unit, locale) },
        hasDefault = default != null,
        themeMode = settings.theme.mode,
    )
}

private const val PERCENT = 100

/** The app's palette, following the app's own theme choice; only "follow system" lets the launcher decide. */
internal fun widgetColors(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> ColorProviders(light = LightColors, dark = DarkColors)
    ThemeMode.LIGHT -> ColorProviders(light = LightColors, dark = LightColors)
    ThemeMode.DARK -> ColorProviders(light = DarkColors, dark = DarkColors)
}

/** Today's progress with a one-tap button for the default container. Compact (2x1) and large (4x2) layouts. */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(COMPACT, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = loadModel(context)
        val open = ComponentName(context.packageName, MAIN_ACTIVITY)
        provideContent {
            GlanceTheme(colors = widgetColors(model.themeMode)) {
                WidgetContent(model, androidx.glance.LocalSize.current.height >= LARGE.height, open)
            }
        }
    }

    companion object {
        val COMPACT = DpSize(110.dp, 40.dp)
        val LARGE = DpSize(250.dp, 110.dp)

        /** `:widget` cannot see `:app`, so the launcher activity is named. */
        private const val MAIN_ACTIVITY = "casa.peterson.dhmo.MainActivity"
    }
}

@Composable
private fun WidgetContent(model: WidgetModel, large: Boolean, open: ComponentName) {
    val label = if (model.goalText != null) "${model.consumedText} / ${model.goalText}" else model.consumedText
    Column(
        modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).padding(12.dp)
            .clickable(actionStartActivity(open)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    label,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = if (large) 20.sp else 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                if (large && model.goalText != null) {
                    Text(
                        "${(model.fraction * PERCENT).toInt()}%",
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                    )
                }
            }
            if (model.hasDefault && model.defaultLabel != null) {
                Text(
                    model.defaultLabel,
                    modifier = GlanceModifier.background(GlanceTheme.colors.primary).padding(horizontal = 12.dp, vertical = 8.dp)
                        .clickable(actionRunCallback<LogDefaultDrinkAction>()),
                    style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
        if (model.goalText != null) {
            Spacer(GlanceModifier.height(if (large) 12.dp else 6.dp))
            LinearProgressIndicator(
                progress = model.fraction,
                modifier = GlanceModifier.fillMaxWidth(),
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.surfaceVariant,
            )
        }
        Spacer(GlanceModifier.width(0.dp))
    }
}

/** Logs the default container (sort order 0) and refreshes every widget. */
class LogDefaultDrinkAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val ep = entryPoint(context)
        val default = ep.containers().getAll().minByOrNull { it.sortOrder } ?: return
        ep.logIntake()(default.volumeMl, Source.WIDGET)
        TodayWidget().updateAll(context)
    }
}
