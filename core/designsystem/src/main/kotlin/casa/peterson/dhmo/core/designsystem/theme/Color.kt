package casa.peterson.dhmo.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * The lo-fi theme: a small room at dusk. "Day" is Late Afternoon Moss (sage paper, river-slate water, lamp amber used
 * as glow only); "Night" is Midnight Desk (indigo rather than black, lamp-paper text). Every text pair below was
 * checked against WCAG AA (4.5:1), and outlines used by controls against 3:1.
 *
 * These are also the fallback schemes when dynamic colour is off (the default) or unavailable below Android 12.
 */

/** The Day (light) Material scheme, public so the widget can theme itself with the same colours. */
val LightColors =
    lightColorScheme(
        primary = Color(0xFF2D6A76),
        onPrimary = Color(0xFFF8F7EF),
        primaryContainer = Color(0xFFCFE5E6),
        onPrimaryContainer = Color(0xFF0F3237),
        secondary = Color(0xFF8A5410),
        onSecondary = Color(0xFFF8F7EF),
        secondaryContainer = Color(0xFFF6DDB4),
        onSecondaryContainer = Color(0xFF3B2400),
        tertiary = Color(0xFF3F6834),
        onTertiary = Color(0xFFF8F7EF),
        tertiaryContainer = Color(0xFFCDE8BD),
        onTertiaryContainer = Color(0xFF12330A),
        error = Color(0xFFA5391F),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD2),
        onErrorContainer = Color(0xFF410A00),
        background = Color(0xFFECEEE3),
        onBackground = Color(0xFF22302B),
        surface = Color(0xFFECEEE3),
        onSurface = Color(0xFF22302B),
        surfaceVariant = Color(0xFFDFE4D3),
        onSurfaceVariant = Color(0xFF56625B),
        surfaceTint = Color(0xFF2D6A76),
        outline = Color(0xFF7A8478),
        outlineVariant = Color(0xFFC3C9B6),
        inverseSurface = Color(0xFF22302B),
        inverseOnSurface = Color(0xFFF8F7EF),
        inversePrimary = Color(0xFF82C6CC),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFF8F7EF),
        surfaceDim = Color(0xFFD9DCCD),
        surfaceContainerLowest = Color(0xFFFBFAF3),
        surfaceContainerLow = Color(0xFFF8F7EF),
        surfaceContainer = Color(0xFFF1F2E7),
        surfaceContainerHigh = Color(0xFFE6EADB),
        surfaceContainerHighest = Color(0xFFDFE4D3),
    )

/** The Night (dark) Material scheme. */
val DarkColors =
    darkColorScheme(
        primary = Color(0xFF82C6CC),
        onPrimary = Color(0xFF161A2C),
        primaryContainer = Color(0xFF1F4F58),
        onPrimaryContainer = Color(0xFFCDEDEF),
        secondary = Color(0xFFF2B661),
        onSecondary = Color(0xFF3B2400),
        secondaryContainer = Color(0xFF5A3B0E),
        onSecondaryContainer = Color(0xFFFFDDB0),
        tertiary = Color(0xFF9FCB8C),
        onTertiary = Color(0xFF123308),
        tertiaryContainer = Color(0xFF2F4F25),
        onTertiaryContainer = Color(0xFFBBE8A8),
        error = Color(0xFFEE9A72),
        onError = Color(0xFF4A1800),
        errorContainer = Color(0xFF6B2D0E),
        onErrorContainer = Color(0xFFFFDBCB),
        background = Color(0xFF161A2C),
        onBackground = Color(0xFFEDE6D3),
        surface = Color(0xFF161A2C),
        onSurface = Color(0xFFEDE6D3),
        surfaceVariant = Color(0xFF2A3050),
        onSurfaceVariant = Color(0xFFA8A9C2),
        surfaceTint = Color(0xFF82C6CC),
        outline = Color(0xFF6F7396),
        outlineVariant = Color(0xFF3B4163),
        inverseSurface = Color(0xFFEDE6D3),
        inverseOnSurface = Color(0xFF22302B),
        inversePrimary = Color(0xFF2D6A76),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF313857),
        surfaceDim = Color(0xFF12162A),
        surfaceContainerLowest = Color(0xFF12162A),
        surfaceContainerLow = Color(0xFF1F243B),
        surfaceContainer = Color(0xFF232845),
        surfaceContainerHigh = Color(0xFF2A3050),
        surfaceContainerHighest = Color(0xFF313857),
    )

/**
 * Colours the Material roles have no slot for: the window's sky, sun, haze, clouds and skyline, the lamp's glow, the
 * wooden sill, the rain and the water. They are decoration, never the only carrier of meaning, so they have no
 * contrast requirement of their own. Values are the design's tokens for each theme.
 */
@Immutable
class DhmoExtraColors(
    val skyTop: Color,
    val skyMiddle: Color,
    val skyBottom: Color,
    val sun: Color,
    val haze: Color,
    val cloudHigh: Color,
    val cloudLow: Color,
    val skyline: Color,
    val star: Color,
    val starfield: Color,
    val water: Color,
    val glass: Color,
    val shine: Color,
    val glow: Color,
    val glowHalo: Color,
    val wood: Color,
    val woodHighlight: Color,
    val rain: Color,
    val shelfHighlight: Color,
    val isNight: Boolean,
)

internal val LightExtraColors =
    DhmoExtraColors(
        skyTop = Color(0xFF9DBFD3),
        skyMiddle = Color(0xFFC9DCE0),
        skyBottom = Color(0xFFF6D6A8),
        sun = Color(0xCCFFCD82),
        haze = Color(0x8CFFF6E2),
        cloudHigh = Color(0xFFFFF8EA),
        cloudLow = Color(0xFFEADFC8),
        skyline = Color(0xFF8FAFBE),
        star = Color(0xFFF8F7EF),
        starfield = Color(0xFF26304A),
        water = Color(0xFF9CC7C9),
        glass = Color(0x59F8F7EF),
        shine = Color(0x8CFFFFFF),
        glow = Color(0xFFE8A54B),
        glowHalo = Color(0x8CE8A54B),
        wood = Color(0xFFB49A74),
        woodHighlight = Color(0xFFD6C29F),
        rain = Color(0xF2FFFFFF),
        shelfHighlight = Color(0xA6FFFFFF),
        isNight = false,
    )

internal val DarkExtraColors =
    DhmoExtraColors(
        skyTop = Color(0xFF2B2F5A),
        skyMiddle = Color(0xFF4A3F6B),
        skyBottom = Color(0xFFC77F6A),
        sun = Color(0x47EDE6D3),
        haze = Color(0x40786EBE),
        cloudHigh = Color(0xFF6B5F8F),
        cloudLow = Color(0xFF3A3862),
        skyline = Color(0xFF10132A),
        star = Color(0xFFEDE6D3),
        starfield = Color(0xFF10132A),
        water = Color(0xFF3E7C86),
        glass = Color(0x12EDE6D3),
        shine = Color(0x38EDE6D3),
        glow = Color(0xFFF2B661),
        glowHalo = Color(0x99F2B661),
        wood = Color(0xFF6B5646),
        woodHighlight = Color(0xFF8A7258),
        rain = Color(0xE6C8D7F0),
        shelfHighlight = Color(0x29F2B661),
        isNight = true,
    )

/** The theme's [DhmoExtraColors]. Provided by `DhmoTheme`; the default is the day set so previews never crash. */
val LocalDhmoExtraColors = staticCompositionLocalOf { LightExtraColors }
