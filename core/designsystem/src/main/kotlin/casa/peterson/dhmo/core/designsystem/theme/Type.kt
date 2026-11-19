package casa.peterson.dhmo.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import casa.peterson.dhmo.core.designsystem.R

/*
 * Three bundled faces (SIL Open Font License, see core/designsystem/licenses), so nothing is fetched at runtime:
 * Fraunces, a soft storybook serif, for display and headlines; Nunito Sans for reading and controls; DM Mono for
 * volumes and times. The two variable fonts need API 26+, and the app's minSdk is 28.
 */

private const val DISPLAY_OPTICAL_SIZE = 72f
private const val SOFT_MAX = 100f
private const val WONKY = 1f

private fun fraunces(weight: FontWeight) = Font(
    R.font.core_designsystem_fraunces_variable,
    weight = weight,
    variationSettings =
        FontVariation.Settings(
            FontVariation.weight(weight.weight),
            FontVariation.Setting("opsz", DISPLAY_OPTICAL_SIZE),
            FontVariation.Setting("SOFT", SOFT_MAX),
            FontVariation.Setting("WONK", WONKY),
        ),
)

private fun nunito(weight: FontWeight) = Font(
    R.font.core_designsystem_nunito_sans_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Soft storybook serif for display and headline styles. */
val DisplayFontFamily = FontFamily(fraunces(FontWeight.Normal), fraunces(FontWeight.SemiBold))

/** Rounded, warm sans for body, titles and labels. */
val BodyFontFamily = FontFamily(nunito(FontWeight.Normal), nunito(FontWeight.SemiBold), nunito(FontWeight.Bold))

/** Monospace for numerals and times, so digits line up. */
val NumeralFontFamily =
    FontFamily(
        Font(R.font.core_designsystem_dm_mono_regular, FontWeight.Normal),
        Font(R.font.core_designsystem_dm_mono_medium, FontWeight.Medium),
    )

private val base = Typography()

private fun TextStyle.display(weight: FontWeight) = copy(fontFamily = DisplayFontFamily, fontWeight = weight)

private fun TextStyle.body(weight: FontWeight? = null) = copy(fontFamily = BodyFontFamily, fontWeight = weight ?: fontWeight)

internal val DhmoTypography =
    Typography(
        displayLarge = base.displayLarge.display(FontWeight.SemiBold),
        displayMedium = base.displayMedium.display(FontWeight.SemiBold),
        displaySmall = base.displaySmall.display(FontWeight.SemiBold),
        headlineLarge = base.headlineLarge.display(FontWeight.Normal),
        headlineMedium = base.headlineMedium.display(FontWeight.Normal),
        headlineSmall = base.headlineSmall.display(FontWeight.Normal),
        titleLarge = base.titleLarge.body(FontWeight.Bold),
        titleMedium = base.titleMedium.body(FontWeight.Bold),
        titleSmall = base.titleSmall.body(FontWeight.Bold),
        bodyLarge = base.bodyLarge.body(FontWeight.Normal),
        bodyMedium = base.bodyMedium.body(FontWeight.Normal),
        bodySmall = base.bodySmall.body(FontWeight.Normal),
        labelLarge = base.labelLarge.body(FontWeight.SemiBold),
        labelMedium = base.labelMedium.body(FontWeight.SemiBold),
        labelSmall = base.labelSmall.body(FontWeight.SemiBold),
    )

/** The big volume figure on the Today screen: monospaced and tabular, 48 sp. */
val NumeralHeroStyle =
    TextStyle(fontFamily = NumeralFontFamily, fontWeight = FontWeight.Medium, fontSize = 48.sp, lineHeight = 52.sp)

/** Smaller monospaced figures: quick-add amounts, row volumes, times. */
val NumeralLabelStyle =
    TextStyle(fontFamily = NumeralFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
