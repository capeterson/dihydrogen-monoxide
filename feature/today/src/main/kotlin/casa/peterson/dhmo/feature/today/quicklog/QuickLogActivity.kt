package casa.peterson.dhmo.feature.today.quicklog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.data.usecase.intake.LogIntakeUseCase
import casa.peterson.dhmo.core.designsystem.component.VolumeInputSheetContent
import casa.peterson.dhmo.core.designsystem.theme.DhmoTheme
import casa.peterson.dhmo.core.model.Source
import casa.peterson.dhmo.core.model.settings.AppSettings
import casa.peterson.dhmo.feature.today.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Dialog activity for logging a custom amount from a reminder. */
@AndroidEntryPoint
class QuickLogActivity : ComponentActivity() {
    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var logIntake: LogIntakeUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val current by settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            DhmoTheme {
                VolumeInputSheetContent(
                    title = stringResource(R.string.feature_today_quick_log_title),
                    initialMl = 250,
                    unit = current.units,
                    onConfirm = { amount ->
                        lifecycleScope.launch {
                            logIntake(amount, Source.NOTIFICATION)
                            finish()
                        }
                    },
                    onDismiss = ::finish,
                    minMl = 1,
                    maxMl = 10_000,
                )
            }
        }
    }

    companion object {
        const val ACTION_QUICK_LOG = "casa.peterson.dhmo.action.QUICK_LOG"
    }
}
