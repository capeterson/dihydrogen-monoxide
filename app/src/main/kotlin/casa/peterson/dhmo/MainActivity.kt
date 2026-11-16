package casa.peterson.dhmo

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.core.designsystem.theme.DhmoTheme
import casa.peterson.dhmo.core.model.settings.ThemeMode
import casa.peterson.dhmo.ui.DhmoApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { viewModel.startState.value == StartState.Loading }
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // The bottom bar draws its own colour behind the gesture or 3-button bar; no system scrim on top.
            window.isNavigationBarContrastEnforced = false
        }
        // On a fresh start, DhmoApp follows the launch deep link once it knows onboarding is complete. On a
        // recreation the NavController restores its own back stack, so the link is not followed again.
        val launchDeepLink = takeDeepLink().takeIf { savedInstanceState == null }
        setContent {
            val startState by viewModel.startState.collectAsStateWithLifecycle()
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val darkTheme =
                when (theme.mode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            DhmoTheme(darkTheme = darkTheme, dynamicColor = theme.dynamicColor) {
                if (startState != StartState.Loading) DhmoApp(startState = startState, launchDeepLink = launchDeepLink)
            }
        }
    }

    /** `singleTop`: notification, shortcut and tile taps arrive here while the app is open. DhmoApp listens for them. */
    override fun onNewIntent(intent: Intent) {
        setIntent(intent)
        super.onNewIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // T-WX2 hook: call WeatherController.refreshIfStale() and LocationRefresher.refreshIfForeground() here.
    }

    /**
     * Removes the deep link from the launch intent and returns a copy of the intent that still has it, or null when
     * there is none. NavHost would otherwise follow `intent.data` as soon as its graph is set, before the app knows
     * whether onboarding is complete, and so let a deep link skip onboarding.
     */
    private fun takeDeepLink(): Intent? {
        if (intent?.data == null) return null
        val copy = Intent(intent)
        intent = Intent(intent).setData(null)
        return copy
    }
}
