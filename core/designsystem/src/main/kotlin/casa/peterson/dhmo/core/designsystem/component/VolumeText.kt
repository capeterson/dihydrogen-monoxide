package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import casa.peterson.dhmo.core.domain.format.VolumeFormatter
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import java.util.Locale

/** The locale of the current configuration. */
@Composable
internal fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** A volume in millilitres shown in the user's [unit], formatted for the current locale: `1,250 ml`, `1.25 L`, `8.5 fl oz`. */
@Composable
fun VolumeText(
    ml: Int,
    unit: VolumeUnit,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    showUnit: Boolean = true,
) {
    val locale = currentLocale()
    val text = if (showUnit) VolumeFormatter.format(ml, unit, locale) else VolumeFormatter.formatNumber(ml, unit, locale)
    Text(text = text, modifier = modifier, style = style, color = color, textAlign = textAlign)
}
