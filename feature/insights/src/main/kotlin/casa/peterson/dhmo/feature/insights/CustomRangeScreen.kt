package casa.peterson.dhmo.feature.insights

import androidx.compose.runtime.Composable
import java.time.LocalDate

/** A custom range opened directly (`CustomRangeRoute`): the Insights screen with a back button, seeded from the route. */
@Composable
internal fun CustomRangeScreen(onBack: () -> Unit, onOpenDay: (LocalDate) -> Unit, onOpenCorrelation: (String) -> Unit) {
    InsightsScreen(onOpenDay = onOpenDay, onOpenCorrelation = onOpenCorrelation, onBack = onBack)
}
