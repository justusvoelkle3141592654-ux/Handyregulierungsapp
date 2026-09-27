package de.handyzeitvertreib.app.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun animatedProgress(target: Float): Float {
    val reduce = LocalReduceMotion.current
    val value by animateFloatAsState(target.coerceIn(0f, 1f), if (reduce) snap() else tween(600), label = "progress")
    return value
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    stroke: Dp = 8.dp,
    reached: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    val colors = HzvTheme.colors
    val animated = animatedProgress(progress)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val strokePx = stroke.toPx()
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(strokePx / 2, strokePx / 2)
            drawArc(colors.track, 0f, 360f, false, topLeft, arcSize, style = Stroke(strokePx))
            drawArc(
                if (reached) colors.limitReached else colors.accent,
                -90f,
                360f * animated,
                false,
                topLeft,
                arcSize,
                style = Stroke(strokePx, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    reached: Boolean = false,
) {
    val colors = HzvTheme.colors
    val animated = animatedProgress(progress)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 8.dp)
            .clip(HzvShapes.pill)
            .background(colors.track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .heightIn(min = 8.dp)
                .clip(HzvShapes.pill)
                .background(if (reached) colors.limitReached else colors.accent),
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = HzvSpacing.section, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = HzvTheme.colors.textPrimary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        action?.invoke()
    }
}

enum class ChipTone { NEUTRAL, OK, WARNING, REACHED }

/** Status chip that always pairs colour with an icon and text, never colour alone. */
@Composable
fun LimitStatusChip(
    text: String,
    tone: ChipTone,
    modifier: Modifier = Modifier,
) {
    val colors = HzvTheme.colors
    val (color, icon) =
        when (tone) {
            ChipTone.NEUTRAL -> colors.textSecondary to Icons.Outlined.Info
            ChipTone.OK -> colors.success to Icons.Outlined.CheckCircle
            ChipTone.WARNING -> colors.warning to Icons.Outlined.HourglassBottom
            ChipTone.REACHED -> colors.limitReached to Icons.Outlined.ErrorOutline
        }
    Row(
        modifier
            .clip(HzvShapes.pill)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

@Composable
fun AppRow(
    packageName: String,
    label: String,
    supporting: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    bottom: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(HzvShapes.inner)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(packageName, label, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = HzvTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (supporting != null) {
                    Text(supporting, style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary, maxLines = 2)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
        if (bottom != null) {
            Spacer(Modifier.padding(top = 8.dp))
            bottom()
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = HzvShapes.pill,
        colors = ButtonDefaults.buttonColors(containerColor = HzvTheme.colors.accent, contentColor = HzvTheme.colors.onAccent),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, textAlign = TextAlign.Center)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 52.dp), shape = HzvShapes.pill) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = HzvTheme.colors.textPrimary, textAlign = TextAlign.Center)
    }
}

/** Card for a permission or capability with its current status and one clear action. */
@Composable
fun PermissionStatusCard(
    title: String,
    body: String,
    granted: Boolean,
    grantedLabel: String,
    missingLabel: String,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            LimitStatusChip(if (granted) grantedLabel else missingLabel, if (granted) ChipTone.OK else ChipTone.WARNING)
        }
        Spacer(Modifier.padding(top = 6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary)
        if (actionLabel != null) {
            Spacer(Modifier.padding(top = 12.dp))
            SecondaryButton(actionLabel, onAction, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    GlassCard(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = HzvTheme.colors.accent, modifier = Modifier.size(40.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.textPrimary, textAlign = TextAlign.Center)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = HzvTheme.colors.textSecondary, textAlign = TextAlign.Center)
            if (actionLabel != null) {
                Spacer(Modifier.padding(top = 4.dp))
                PrimaryButton(actionLabel, onAction)
            }
        }
    }
}

@Composable
fun ErrorState(
    title: String,
    body: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) = EmptyState(Icons.Outlined.ErrorOutline, title, body, modifier, retryLabel, onRetry)

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) HzvTheme.colors.limitReached else HzvTheme.colors.accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}

data class BarDatum(
    val label: String,
    val value: Long,
    val accessibilityValue: String,
    val available: Boolean = true,
    val highlighted: Boolean = false,
)

/**
 * Simple bar chart. The whole chart carries one content description listing every bar,
 * so screen readers get the data instead of a picture.
 */
@Composable
fun BarChart(
    data: List<BarDatum>,
    chartDescription: String,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
) {
    val colors = HzvTheme.colors
    val max = (data.maxOfOrNull { it.value } ?: 0L).coerceAtLeast(1L)
    val description = chartDescription + ": " + data.joinToString("; ") { "${it.label} ${it.accessibilityValue}" }
    val grow = animatedProgress(1f)
    Column(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val gap = 8.dp.toPx()
            val count = data.size.coerceAtLeast(1)
            val barWidth = (size.width - gap * (count - 1)) / count
            val minBar = 4.dp.toPx()
            data.forEachIndexed { index, datum ->
                val fraction = datum.value.toFloat() / max * grow
                val barHeight = if (datum.available) (size.height * fraction).coerceAtLeast(minBar) else minBar
                val color =
                    when {
                        !datum.available -> colors.track
                        datum.highlighted -> colors.accent
                        else -> colors.accent.copy(alpha = 0.55f)
                    }
                drawRoundRect(
                    color,
                    topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius =
                        androidx.compose.ui.geometry
                            .CornerRadius(10.dp.toPx()),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            data.forEach {
                Text(
                    it.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (it.highlighted) colors.textPrimary else colors.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
