package casa.peterson.dhmo.feature.today.quicklog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.res.stringResource
import casa.peterson.dhmo.core.designsystem.component.PlaceholderScreen
import casa.peterson.dhmo.core.designsystem.theme.DhmoTheme
import casa.peterson.dhmo.feature.today.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * Logs a custom amount from a notification (intent action `casa.peterson.dhmo.action.QUICK_LOG`, not exported).
 * A placeholder until T9.
 */
@AndroidEntryPoint
class QuickLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DhmoTheme { PlaceholderScreen(title = stringResource(R.string.feature_today_quick_log_title)) } }
    }

    companion object {
        const val ACTION_QUICK_LOG = "casa.peterson.dhmo.action.QUICK_LOG"
    }
}
