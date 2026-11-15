package casa.peterson.dhmo.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Fallback schemes for devices without dynamic colour (below Android 12) or when it is switched off. */
internal val LightColors =
    lightColorScheme(
        primary = Color(0xFF00658E),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFC6E7FF),
        onPrimaryContainer = Color(0xFF001E2F),
        secondary = Color(0xFF4F616E),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD2E5F5),
        onSecondaryContainer = Color(0xFF0A1D29),
        tertiary = Color(0xFF00696B),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFF6FF7F9),
        onTertiaryContainer = Color(0xFF002020),
        background = Color(0xFFF7FAFD),
        onBackground = Color(0xFF181C20),
        surface = Color(0xFFF7FAFD),
        onSurface = Color(0xFF181C20),
    )

internal val DarkColors =
    darkColorScheme(
        primary = Color(0xFF86CFFF),
        onPrimary = Color(0xFF00344E),
        primaryContainer = Color(0xFF004B6F),
        onPrimaryContainer = Color(0xFFC6E7FF),
        secondary = Color(0xFFB7C9D9),
        onSecondary = Color(0xFF21333F),
        secondaryContainer = Color(0xFF384956),
        onSecondaryContainer = Color(0xFFD2E5F5),
        tertiary = Color(0xFF4DDADC),
        onTertiary = Color(0xFF003737),
        tertiaryContainer = Color(0xFF004F51),
        onTertiaryContainer = Color(0xFF6FF7F9),
        background = Color(0xFF101417),
        onBackground = Color(0xFFDFE3E7),
        surface = Color(0xFF101417),
        onSurface = Color(0xFFDFE3E7),
    )
