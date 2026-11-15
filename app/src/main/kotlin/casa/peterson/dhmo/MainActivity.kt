package casa.peterson.dhmo

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
                if (startState != StartState.Loading) DhmoApp(startState = startState)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // T-WX2 hook: call WeatherController.refreshIfStale() and LocationRefresher.refreshIfForeground() here.
    }
}
