package casa.peterson.dhmo.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import casa.peterson.dhmo.core.designsystem.component.RecipeCard
import casa.peterson.dhmo.core.designsystem.component.RecipeRow
import casa.peterson.dhmo.core.designsystem.component.SectionCard
import casa.peterson.dhmo.core.designsystem.component.TagChip
import casa.peterson.dhmo.core.designsystem.icon.DhmoIcons
import casa.peterson.dhmo.core.designsystem.theme.NumeralFontFamily
import casa.peterson.dhmo.core.model.ComponentKind
import casa.peterson.dhmo.core.model.GoalBreakdown

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BreakdownCard(breakdown: GoalBreakdown, labels: BreakdownLabels) {
    RecipeCard(title = stringResource(R.string.feature_profile_recipe_title)) {
        breakdown.components.forEach { component ->
            val notApplied = if (component.applied) null else stringResource(R.string.feature_profile_breakdown_not_applied)
            RecipeRow(
                icon = ingredientIcon(component.kind),
                label = stringResource(ingredientName(component.kind)),
                value = if (isBase(component.kind)) labels.volume(component.ml) else labels.signed(component.ml),
                detail = notApplied ?: labels.note(component) ?: labels.detail(component),
                modifier = Modifier.semantics(mergeDescendants = true) {},
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.feature_profile_makes),
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                labels.volume(breakdown.calculatedMl),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = NumeralFontFamily, fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Text(stringResource(R.string.feature_profile_drinks_only), style = MaterialTheme.typography.bodySmall)
    }
}

/** Shows the inputs that counted toward the calculated goal. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WhatWentInCard(breakdown: GoalBreakdown, labels: BreakdownLabels) {
    val inputs = labels.inputs(breakdown.components)
    if (inputs.isEmpty()) return
    SectionCard(title = stringResource(R.string.feature_profile_what_went_in)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            inputs.forEach { (name, value) -> TagChip(label = stringResource(name), value = value) }
        }
    }
}

private fun ingredientIcon(kind: ComponentKind): Int = when (kind) {
    ComponentKind.BASE_WEIGHT -> DhmoIcons.Scale
    ComponentKind.ACTIVITY_LEVEL, ComponentKind.ACTIVITY_MINUTES -> DhmoIcons.Bolt
    ComponentKind.CLIMATE_MANUAL, ComponentKind.CLIMATE_FORECAST -> DhmoIcons.Sun
    else -> DhmoIcons.WaterDrop
}

private fun ingredientName(kind: ComponentKind): Int = when (kind) {
    ComponentKind.BASE_CHILD_TABLE, ComponentKind.BASE_GENDER_FLOOR, ComponentKind.BASE_WEIGHT -> R.string.feature_profile_ingredient_base
    ComponentKind.PREGNANCY -> R.string.feature_profile_ingredient_pregnancy
    ComponentKind.LACTATION -> R.string.feature_profile_ingredient_lactation
    ComponentKind.ACTIVITY_LEVEL, ComponentKind.ACTIVITY_MINUTES -> R.string.feature_profile_ingredient_active
    ComponentKind.CLIMATE_FORECAST -> R.string.feature_profile_ingredient_forecast
    ComponentKind.CLIMATE_MANUAL -> R.string.feature_profile_ingredient_climate
    ComponentKind.ALTITUDE -> R.string.feature_profile_ingredient_altitude
}

/** The base is the starting amount, not an addition, so it is shown without a plus sign. */
private fun isBase(kind: ComponentKind): Boolean =
    kind == ComponentKind.BASE_WEIGHT || kind == ComponentKind.BASE_CHILD_TABLE || kind == ComponentKind.BASE_GENDER_FLOOR
