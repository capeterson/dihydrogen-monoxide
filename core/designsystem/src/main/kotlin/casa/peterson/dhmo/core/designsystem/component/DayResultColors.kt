package casa.peterson.dhmo.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import casa.peterson.dhmo.core.designsystem.R
import casa.peterson.dhmo.core.designsystem.icon.DhmoIcons

/** How a day went against its goal. */
enum class DayResult { MET, PARTLY_MET, MISSED }

/** Colours, icon and label for one [DayResult]. The icon and label make the result readable without colour. */
@Immutable
class DayResultStyle(val container: Color, val onContainer: Color, @DrawableRes val icon: Int, @StringRes val label: Int)

/**
 * Colour-blind-safe colours for [DayResult], from the Okabe-Ito palette (blue, amber, vermillion), which stay
 * distinct under deuteranopia, protanopia and tritanopia. The three also differ in lightness and always come with
 * a different icon (check, dash, cross) and a text label, so colour is never the only carrier of meaning.
 */
object DayResultColors {
    private val MetLight = Color(0xFF0072B2)
    private val PartlyLight = Color(0xFF8F6200)
    private val MissedLight = Color(0xFFB03F00)
    private val MetDark = Color(0xFF56B4E9)
    private val PartlyDark = Color(0xFFE6B422)
    private val MissedDark = Color(0xFFFF9B6B)

    /** The style for [result] on a light or dark ([darkTheme]) surface. */
    fun style(result: DayResult, darkTheme: Boolean): DayResultStyle {
        val onContainer = if (darkTheme) Color.Black else Color.White
        return when (result) {
            DayResult.MET ->
                DayResultStyle(
                    if (darkTheme) MetDark else MetLight,
                    onContainer,
                    DhmoIcons.Check,
                    R.string.core_designsystem_day_result_met,
                )

            DayResult.PARTLY_MET ->
                DayResultStyle(
                    if (darkTheme) PartlyDark else PartlyLight,
                    onContainer,
                    DhmoIcons.Remove,
                    R.string.core_designsystem_day_result_partly_met,
                )

            DayResult.MISSED ->
                DayResultStyle(
                    if (darkTheme) MissedDark else MissedLight,
                    onContainer,
                    DhmoIcons.Close,
                    R.string.core_designsystem_day_result_missed,
                )
        }
    }

    /** The style for [result] matching the current theme. */
    @Composable
    fun style(result: DayResult): DayResultStyle = style(result, MaterialTheme.colorScheme.background.luminance() < DARK_LUMINANCE)

    private const val DARK_LUMINANCE = 0.5f
}

/** A coloured circle with the result's icon and, when [showLabel] is on, its text label. */
@Composable
fun DayResultBadge(result: DayResult, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    val style = DayResultColors.style(result)
    val label = stringResource(style.label)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(style.container), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(style.icon),
                contentDescription = if (showLabel) null else label,
                modifier = Modifier.padding(2.dp),
                tint = style.onContainer,
            )
        }
        if (showLabel) Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
