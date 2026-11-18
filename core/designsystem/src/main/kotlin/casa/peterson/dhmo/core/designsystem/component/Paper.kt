package casa.peterson.dhmo.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import casa.peterson.dhmo.core.designsystem.theme.LocalDhmoExtraColors
import casa.peterson.dhmo.core.designsystem.theme.NumeralFontFamily

/** A thin dashed rule along the bottom edge, the design's `border-bottom: 1px dashed var(--outline)`. */
fun Modifier.dashedBottomRule(color: androidx.compose.ui.graphics.Color, enabled: Boolean = true): Modifier = if (!enabled) {
    this
} else {
    drawBehind {
        drawLine(
            color,
            Offset(0f, size.height - 0.5f),
            Offset(size.width, size.height - 0.5f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
        )
    }
}

/**
 * The ruled-notebook card: a title and [count] on top, a faint red margin line down the left, and [content] (usually
 * [NotebookRow]s) below it.
 */
@Composable
fun NotebookCard(title: String, count: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val margin = MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
    PaperCard(modifier) {
        Column(
            Modifier.drawBehind { drawLine(margin, Offset(24.dp.toPx(), 0f), Offset(24.dp.toPx(), size.height), strokeWidth = 1.dp.toPx()) }
                .padding(start = 34.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(
                    count,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = NumeralFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

/** One line in a [NotebookCard]: a time in the margin, the container's inked [icon], the [amount] and an optional [label]. */
@Composable
fun NotebookRow(
    time: String,
    amount: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    label: String? = null,
    last: Boolean = false,
) {
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).dashedBottomRule(MaterialTheme.colorScheme.outlineVariant, !last),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            time,
            Modifier.defaultMinSize(minWidth = 44.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = NumeralFontFamily, fontSize = 13.sp, lineHeight = 18.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (icon != null) Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(
                amount,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = NumeralFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                ),
            )
            if (label != null) Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** An insight: a lamp-coloured round [icon] beside a [title] and [body]. */
@Composable
fun InsightCard(title: String, body: String, @DrawableRes icon: Int, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val extras = LocalDhmoExtraColors.current
    PaperCard(modifier) {
        Row(
            Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(extras.glow), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = if (extras.isNight) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The design's `.seg`: a rounded pill holding mutually exclusive [options], the [selected] one filled in the primary colour. */
@Composable
fun SegmentedPill(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp)
            .selectableGroup()
            .semantics { if (description != null) contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, text ->
            val on = index == selected
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(index) })
                    .defaultMinSize(minHeight = 36.dp)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One ingredient in the goal's recipe: a round [icon], a [label] and a monospaced [value], over a dashed rule. */
@Composable
fun RecipeRow(@DrawableRes icon: Int, label: String, value: String, modifier: Modifier = Modifier, last: Boolean = false) {
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).dashedBottomRule(MaterialTheme.colorScheme.outlineVariant, !last),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = NumeralFontFamily, fontWeight = FontWeight.Medium))
    }
}
