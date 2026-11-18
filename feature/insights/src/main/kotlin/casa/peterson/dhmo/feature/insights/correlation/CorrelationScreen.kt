package casa.peterson.dhmo.feature.insights.correlation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.core.designsystem.component.DhmoScreen
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.designsystem.component.EmptyState
import casa.peterson.dhmo.core.designsystem.icon.DhmoIcons
import casa.peterson.dhmo.core.domain.format.VolumeFormatter
import casa.peterson.dhmo.core.domain.stats.CorrelationStrength
import casa.peterson.dhmo.feature.insights.R
import casa.peterson.dhmo.feature.insights.domain.CorrelationDriver
import java.util.Locale

@Composable
internal fun CorrelationScreen(onBack: () -> Unit, viewModel: CorrelationViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CorrelationContent(state, onBack)
}

@Composable
internal fun CorrelationContent(state: CorrelationUiState, onBack: () -> Unit) {
    val title = stringResource(
        if (state.driver == CorrelationDriver.TEMPERATURE) R.string.feature_insights_correlation_title_temperature else R.string.feature_insights_correlation_title_activity,
    )
    DhmoScreen(topBar = { DhmoTopBar(title, onNavigateBack = onBack) }) { padding ->
        when {
            state.loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.result == null -> EmptyState(
                title = title,
                message = stringResource(R.string.feature_insights_correlation_not_enough, state.pairs.size),
                icon = DhmoIcons.BarChart,
                modifier = Modifier.padding(padding),
            )

            else -> Column(
                Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val result = state.result
                Text(description(state), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.feature_insights_correlation_basis, result.n),
                    style = MaterialTheme.typography.bodyMedium,
                )
                ScatterPlot(state)
                Text(stringResource(R.string.feature_insights_correlation_disclaimer), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun description(state: CorrelationUiState): String {
    val result = requireNotNull(state.result)
    val temperature = state.driver == CorrelationDriver.TEMPERATURE
    val sentence = when {
        result.strength == CorrelationStrength.NONE -> if (temperature) {
            R.string.feature_insights_correlation_none_temperature
        } else {
            R.string.feature_insights_correlation_none_activity
        }

        temperature && result.positive -> R.string.feature_insights_text_temperature_more
        temperature -> R.string.feature_insights_text_temperature_less
        result.positive -> R.string.feature_insights_text_activity_more
        else -> R.string.feature_insights_text_activity_less
    }
    val strength = when (result.strength) {
        CorrelationStrength.STRONG -> R.string.feature_insights_strength_strong
        CorrelationStrength.MODERATE -> R.string.feature_insights_strength_moderate
        CorrelationStrength.WEAK, CorrelationStrength.NONE -> R.string.feature_insights_strength_weak
    }
    return if (result.strength == CorrelationStrength.NONE) {
        stringResource(sentence)
    } else {
        stringResource(R.string.feature_insights_text_correlation, stringResource(sentence), stringResource(strength))
    }
}

@Composable
private fun ScatterPlot(state: CorrelationUiState) {
    val locale = Locale.getDefault()
    val xs = state.pairs.map { it.first }
    val ys = state.pairs.map { it.second }
    val minX = xs.min()
    val maxX = xs.max()
    val minY = ys.min()
    val maxY = ys.max()
    val summary = stringResource(R.string.feature_insights_correlation_plot_description, state.pairs.size)
    val dot = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outline
    val xLabel = stringResource(
        if (state.driver == CorrelationDriver.TEMPERATURE) R.string.feature_insights_correlation_x_temperature else R.string.feature_insights_correlation_x_activity,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.feature_insights_correlation_y_axis, VolumeFormatter.format(maxY.toInt(), state.unit, locale)),
            style = MaterialTheme.typography.labelSmall,
        )
        Canvas(Modifier.fillMaxWidth().height(240.dp).semantics { contentDescription = summary }) {
            val pad = 8.dp.toPx()
            val w = size.width - 2 * pad
            val h = size.height - 2 * pad
            drawLine(axis, Offset(pad, size.height - pad), Offset(size.width - pad, size.height - pad), strokeWidth = 2f)
            drawLine(axis, Offset(pad, pad), Offset(pad, size.height - pad), strokeWidth = 2f)
            val spanX = (maxX - minX).takeIf { it > 0 } ?: 1.0
            val spanY = (maxY - minY).takeIf { it > 0 } ?: 1.0
            state.pairs.forEach { (x, y) ->
                val cx = pad + ((x - minX) / spanX * w).toFloat()
                val cy = size.height - pad - ((y - minY) / spanY * h).toFloat()
                drawCircle(dot, radius = 5.dp.toPx(), center = Offset(cx, cy), alpha = 0.7f)
                drawCircle(dot, radius = 5.dp.toPx(), center = Offset(cx, cy), style = Stroke(1f))
            }
        }
        Text(
            "$xLabel: ${axisValue(state, minX)} – ${axisValue(state, maxX)}",
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            stringResource(R.string.feature_insights_correlation_y_range, VolumeFormatter.format(minY.toInt(), state.unit, locale), VolumeFormatter.format(maxY.toInt(), state.unit, locale)),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun axisValue(state: CorrelationUiState, value: Double): String =
    if (state.driver == CorrelationDriver.TEMPERATURE) String.format(Locale.getDefault(), "%.0f°C", value) else String.format(Locale.getDefault(), "%.0f min", value)
