package casa.peterson.dhmo.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * The lo-fi app theme: the Day and Night palettes, bundled type and soft shapes. Dynamic colour on Android 12+ is an
 * opt-in ([dynamicColor], off by default) that swaps the palette for the wallpaper's; the type, shapes and the
 * window's sky, lamp and wood stay as they are.
 */
@Composable
fun DhmoTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> DarkColors

            else -> LightColors
        }
    CompositionLocalProvider(LocalDhmoExtraColors provides if (darkTheme) DarkExtraColors else LightExtraColors) {
        MaterialTheme(colorScheme = colorScheme, typography = DhmoTypography, shapes = DhmoShapes) {
            // The root surface paints the theme's background and sets the default content colour. Without it,
            // screens drawn outside a Scaffold (onboarding) fall back to the window's colour and black text.
            Surface(
                modifier = Modifier.fillMaxSize().paperGrain(darkTheme),
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ) {
                content()
            }
        }
    }
}
