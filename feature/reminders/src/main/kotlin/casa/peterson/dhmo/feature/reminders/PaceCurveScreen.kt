package casa.peterson.dhmo.feature.reminders

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.domain.pace.PaceCurve
import casa.peterson.dhmo.core.model.settings.CurvePoint
import casa.peterson.dhmo.core.model.settings.PaceCurveSettings
import casa.peterson.dhmo.core.model.settings.PaceCurveType
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

@Composable
internal fun PaceCurveScreen(onBack: () -> Unit, viewModel: RemindersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PaceCurveContent(state.settings.curve, onBack, viewModel::setCurve)
}

internal data class CurveDraftPoint(val x: String, val y: String)

internal fun pointsFromDraft(draft: List<CurveDraftPoint>): List<CurvePoint>? {
    val points = draft.mapNotNull {
        val x = parsePercent(it.x)
        val y = parsePercent(it.y)
        if (x == null || y == null) null else CurvePoint(x, y)
    }
    return points.takeIf { it.size == draft.size && PaceCurve.validate(it) }
}

private fun parsePercent(value: String): Int? = try {
    val permille = BigDecimal(value).movePointRight(1).setScale(0, RoundingMode.UNNECESSARY).intValueExact()
    permille.takeIf { it in 0..FULL_PERMILLE }
} catch (_: NumberFormatException) {
    null
} catch (_: ArithmeticException) {
    null
}

private fun draftFrom(points: List<CurvePoint>) = points.map {
    CurveDraftPoint(formatPercent(it.xPermille), formatPercent(it.yPermille))
}

private fun formatPercent(permille: Int): String = if (permille % PERMILLE_PER_PERCENT == 0) {
    (permille / PERMILLE_PER_PERCENT).toString()
} else {
    String.format(Locale.ROOT, "%.1f", permille.toDouble() / PERMILLE_PER_PERCENT)
}

@Composable
internal fun PaceCurveContent(curve: PaceCurveSettings, onBack: () -> Unit, onSave: (PaceCurveSettings) -> Unit) {
    var selected by remember(curve) { mutableStateOf(curve.type) }
    var draft by remember(curve) {
        mutableStateOf(draftFrom(curve.customPoints.takeIf(PaceCurve::validate) ?: PaceCurve.DEFAULT.points))
    }
    val parsed = pointsFromDraft(draft)
    val preview = when (selected) {
        PaceCurveType.DEFAULT -> PaceCurve.DEFAULT
        PaceCurveType.LINEAR -> PaceCurve.LINEAR
        PaceCurveType.CUSTOM -> parsed?.let(::PaceCurve)
    }
    Scaffold(topBar = { DhmoTopBar(stringResource(R.string.feature_reminders_pace_curve_title), onNavigateBack = onBack) }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.feature_reminders_curve_description), style = MaterialTheme.typography.bodyMedium)
            PaceCurveType.entries.forEach { type ->
                val label = stringResource(
                    when (type) {
                        PaceCurveType.DEFAULT -> R.string.feature_reminders_default_curve
                        PaceCurveType.LINEAR -> R.string.feature_reminders_linear_curve
                        PaceCurveType.CUSTOM -> R.string.feature_reminders_custom_curve
                    },
                )
                Row(
                    Modifier.fillMaxWidth().selectable(selected == type, role = Role.RadioButton, onClick = {
                        selected = type
                        if (type != PaceCurveType.CUSTOM) onSave(curve.copy(type = type))
                    }),
                ) {
                    RadioButton(selected = selected == type, onClick = null)
                    Text(label, Modifier.padding(12.dp))
                }
            }
            preview?.let { CurvePreview(it) }
            if (selected == PaceCurveType.CUSTOM) {
                draft.forEachIndexed { index, point ->
                    val fixed = index == 0 || index == draft.lastIndex
                    Text(stringResource(R.string.feature_reminders_point, index + 1), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = point.x,
                            onValueChange = { draft = draft.replace(index, point.copy(x = it)) },
                            label = { Text(stringResource(R.string.feature_reminders_x)) },
                            enabled = !fixed,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = point.y,
                            onValueChange = { draft = draft.replace(index, point.copy(y = it)) },
                            label = { Text(stringResource(R.string.feature_reminders_y)) },
                            enabled = !fixed,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (!fixed) {
                        TextButton(onClick = { draft = draft.filterIndexed { i, _ -> i != index } }) {
                            Text(stringResource(R.string.feature_reminders_remove))
                        }
                    }
                }
                if (parsed == null) Text(stringResource(R.string.feature_reminders_invalid_curve), color = MaterialTheme.colorScheme.error)
                TextButton(enabled = parsed != null && draft.size < MAX_POINTS, onClick = {
                    parsed?.let { draft = draftFrom(addMidpoint(it)) }
                }) { Text(stringResource(R.string.feature_reminders_add_point)) }
                Button(enabled = parsed != null, onClick = {
                    parsed?.let { onSave(PaceCurveSettings(type = PaceCurveType.CUSTOM, customPoints = it)) }
                }) { Text(stringResource(R.string.feature_reminders_save_curve)) }
            }
        }
    }
}

private fun List<CurveDraftPoint>.replace(index: Int, value: CurveDraftPoint): List<CurveDraftPoint> =
    mapIndexed { i, point -> if (i == index) value else point }

internal fun addMidpoint(points: List<CurvePoint>): List<CurvePoint> {
    val index = points.zipWithNext().indices.maxBy { points[it + 1].xPermille - points[it].xPermille }
    val a = points[index]
    val b = points[index + 1]
    if (b.xPermille - a.xPermille < 2) return points
    val midpoint = CurvePoint((a.xPermille + b.xPermille) / 2, (a.yPermille + b.yPermille) / 2)
    return points.take(index + 1) + midpoint + points.drop(index + 1)
}

@Composable
private fun CurvePreview(curve: PaceCurve) {
    val color = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.feature_reminders_curve_description)
    Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = description }) {
        val path = Path()
        curve.points.forEachIndexed { index, point ->
            val x = size.width * point.xPermille / FULL_PERMILLE
            val y = size.height * (1f - point.yPermille.toFloat() / FULL_PERMILLE)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
    }
}

private const val PERMILLE_PER_PERCENT = 10
private const val FULL_PERMILLE = 1_000
private const val MAX_POINTS = 12
