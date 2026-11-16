package casa.peterson.dhmo.feature.insights.correlation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import casa.peterson.dhmo.core.designsystem.component.DhmoScreen
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.designsystem.component.EmptyState
import casa.peterson.dhmo.core.designsystem.icon.DhmoIcons
import casa.peterson.dhmo.feature.insights.R

/** Placeholder until T-COR (Phase 5) adds the scatter plot. */
@Composable
internal fun CorrelationScreen(onBack: () -> Unit) {
    DhmoScreen(topBar = { DhmoTopBar(stringResource(R.string.feature_insights_correlation_title), onNavigateBack = onBack) }) { padding ->
        EmptyState(
            title = stringResource(R.string.feature_insights_correlation_title),
            message = stringResource(R.string.feature_insights_correlation_placeholder),
            icon = DhmoIcons.BarChart,
            modifier = Modifier.padding(padding),
        )
    }
}
