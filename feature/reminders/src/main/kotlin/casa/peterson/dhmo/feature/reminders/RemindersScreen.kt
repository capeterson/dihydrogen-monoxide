package casa.peterson.dhmo.feature.reminders

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.designsystem.component.SettingsRow
import casa.peterson.dhmo.core.designsystem.component.SwitchRow
import casa.peterson.dhmo.core.designsystem.component.TimePickerRow
import casa.peterson.dhmo.core.model.settings.PaceCurveType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

@Composable
internal fun RemindersScreen(onBack: () -> Unit, onOpenCurve: () -> Unit, viewModel: RemindersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationsAllowed by remember { mutableStateOf(notificationsAllowed(context)) }
    var calendarAllowed by remember { mutableStateOf(calendarAllowed(context)) }
    var calendarDenied by remember { mutableStateOf(false) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = granted && notificationsAllowed(context)
        if (notificationsAllowed) viewModel.setEnabled(true)
    }
    val calendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        calendarAllowed = granted
        calendarDenied = !granted
        if (granted) viewModel.setCalendar(true)
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsAllowed = notificationsAllowed(context)
                calendarAllowed = calendarAllowed(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    RemindersContent(
        state = state,
        notificationsAllowed = notificationsAllowed,
        calendarDenied = calendarDenied || (state.settings.calendarBusyEnabled && !calendarAllowed),
        onBack = onBack,
        onOpenCurve = onOpenCurve,
        onEnabled = { enabled ->
            when {
                !enabled -> viewModel.setEnabled(false)

                notificationsAllowed -> viewModel.setEnabled(true)

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED ->
                    notifications.launch(Manifest.permission.POST_NOTIFICATIONS)

                else -> context.startActivity(notificationSettingsIntent(context.packageName))
            }
        },
        onNotificationSettings = { context.startActivity(notificationSettingsIntent(context.packageName)) },
        onWake = viewModel::setWake,
        onSleep = viewModel::setSleep,
        onInterval = viewModel::setInterval,
        onTolerance = viewModel::setTolerance,
        onEscalation = viewModel::setEscalation,
        onSummary = viewModel::setSummary,
        onCalendar = { enabled ->
            when {
                !enabled -> viewModel.setCalendar(false)
                calendarAllowed -> viewModel.setCalendar(true)
                else -> calendar.launch(Manifest.permission.READ_CALENDAR)
            }
        },
    )
}

private fun notificationsAllowed(context: android.content.Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            )

private fun calendarAllowed(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

private fun notificationSettingsIntent(packageName: String) = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)

@Suppress("LongParameterList") // Screen callbacks keep the presentation independently testable.
@Composable
internal fun RemindersContent(
    state: RemindersUiState,
    notificationsAllowed: Boolean,
    calendarDenied: Boolean,
    onBack: () -> Unit,
    onOpenCurve: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onNotificationSettings: () -> Unit,
    onWake: (Int) -> Unit,
    onSleep: (Int) -> Unit,
    onInterval: (Int) -> Unit,
    onTolerance: (Int) -> Unit,
    onEscalation: (Boolean) -> Unit,
    onSummary: (Boolean) -> Unit,
    onCalendar: (Boolean) -> Unit,
) {
    var numberDialog by remember { mutableStateOf<NumberDialog?>(null) }
    Scaffold(topBar = { DhmoTopBar(stringResource(R.string.feature_reminders_reminders_title), onNavigateBack = onBack) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            if (!notificationsAllowed) {
                Text(
                    stringResource(R.string.feature_reminders_permission_off),
                    Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onNotificationSettings, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.feature_reminders_open_settings))
                }
            }
            SwitchRow(stringResource(R.string.feature_reminders_enabled), state.settings.enabled, onEnabled)
            TimePickerRow(stringResource(R.string.feature_reminders_wake), state.settings.wakeMinutes, onWake)
            TimePickerRow(stringResource(R.string.feature_reminders_sleep), state.settings.sleepMinutes, onSleep)
            state.warning?.let {
                val message = when (it) {
                    WindowWarning.TRUNCATED_BY_DAY_START -> R.string.feature_reminders_truncated
                    WindowWarning.TOO_SHORT -> R.string.feature_reminders_short
                }
                Text(stringResource(message), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            }
            SettingsRow(
                stringResource(R.string.feature_reminders_interval),
                summary = pluralStringResource(
                    R.plurals.feature_reminders_minutes,
                    state.settings.intervalFloorMinutes,
                    state.settings.intervalFloorMinutes,
                ),
                onClick = { numberDialog = NumberDialog.INTERVAL },
            )
            SettingsRow(
                stringResource(R.string.feature_reminders_tolerance),
                summary = stringResource(R.string.feature_reminders_percent, state.settings.tolerancePercent),
                onClick = { numberDialog = NumberDialog.TOLERANCE },
            )
            SwitchRow(stringResource(R.string.feature_reminders_escalation), state.settings.escalationEnabled, onEscalation)
            SwitchRow(stringResource(R.string.feature_reminders_summary), state.settings.endOfDaySummaryEnabled, onSummary)
            SwitchRow(stringResource(R.string.feature_reminders_calendar), state.settings.calendarBusyEnabled, onCalendar)
            if (calendarDenied) Text(stringResource(R.string.feature_reminders_calendar_permission), Modifier.padding(horizontal = 16.dp))
            val curveLabel = when (state.settings.curve.type) {
                PaceCurveType.DEFAULT -> R.string.feature_reminders_default_curve
                PaceCurveType.LINEAR -> R.string.feature_reminders_linear_curve
                PaceCurveType.CUSTOM -> R.string.feature_reminders_custom_curve
            }
            SettingsRow(stringResource(R.string.feature_reminders_curve), summary = stringResource(curveLabel), onClick = onOpenCurve)
            val next = state.nextReminderAtMs?.let {
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it))
            }
            Text(
                if (next ==
                    null
                ) {
                    stringResource(R.string.feature_reminders_next_none)
                } else {
                    stringResource(R.string.feature_reminders_next, next)
                },
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    numberDialog?.let { dialog ->
        val initial = if (dialog == NumberDialog.INTERVAL) state.settings.intervalFloorMinutes else state.settings.tolerancePercent
        val range = if (dialog == NumberDialog.INTERVAL) 15f..240f else 0f..50f
        val label = stringResource(
            if (dialog ==
                NumberDialog.INTERVAL
            ) {
                R.string.feature_reminders_interval
            } else {
                R.string.feature_reminders_tolerance
            },
        )
        var value by remember(dialog) { mutableStateOf(initial.toFloat()) }
        AlertDialog(
            onDismissRequest = { numberDialog = null },
            title = { Text(label) },
            text = {
                Column {
                    Text(
                        if (dialog == NumberDialog.INTERVAL) {
                            pluralStringResource(R.plurals.feature_reminders_minutes, value.roundToInt(), value.roundToInt())
                        } else {
                            stringResource(R.string.feature_reminders_percent, value.roundToInt())
                        },
                    )
                    Slider(value = value, onValueChange = { value = it }, valueRange = range, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (dialog == NumberDialog.INTERVAL) onInterval(value.roundToInt()) else onTolerance(value.roundToInt())
                    numberDialog = null
                }) { Text(stringResource(R.string.feature_reminders_done)) }
            },
            dismissButton = { TextButton(onClick = { numberDialog = null }) { Text(stringResource(R.string.feature_reminders_cancel)) } },
        )
    }
}

private enum class NumberDialog { INTERVAL, TOLERANCE }
